package com.colofabrix.scala.beesight.detection.stages

import cats.syntax.option.*
import com.colofabrix.scala.beesight.collections.NonEmptyWindow.NonEmptyFilled
import com.colofabrix.scala.beesight.config.FreefallConfig
import com.colofabrix.scala.beesight.detection.math.Stats
import com.colofabrix.scala.beesight.detection.model.DetectionData
import java.time.Duration

final class FreefallHandler private (config: FreefallConfig) extends StageHandler {

  protected def detectTrigger(window: NonEmptyFilled[DetectionData]): Boolean =
    val kinematics = window.focus.kinematics

    val speedTrigger =
      kinematics.speed.vertical > config.verticalSpeedThreshold

    val accelerationTrigger =
      kinematics.acceleration > config.accelerationThreshold &&
      kinematics.speed.vertical > config.accelerationMinVelocity

    val isAboveMinAltitude =
      kinematics.altitude > config.minFreefallAltitude

    isAboveMinAltitude && (speedTrigger || accelerationTrigger)

  protected def validateTrigger(window: NonEmptyFilled[DetectionData]): Boolean =
    val focus  = window.focus.kinematics
    val newest = window.newest.kinematics
    val dt     = Duration.between(focus.time, newest.time).toMillis / 1000.0

    val validationDistance = config.validationVerticalSpeed * dt

    newest.altitude < focus.altitude - validationDistance

  protected def findInflection(window: NonEmptyFilled[DetectionData]): Option[Int] =
    val backWindow           = window.leftInclusive
    val discoveredInflection = Stats.findInflectionBy(backWindow)(_.kinematics.speed.vertical)

    discoveredInflection orElse window.focusIndex.some

}

object FreefallHandler {

  def apply(config: FreefallConfig): FreefallHandler =
    new FreefallHandler(config)

}
