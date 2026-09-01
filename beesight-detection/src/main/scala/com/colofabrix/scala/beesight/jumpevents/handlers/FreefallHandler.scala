package com.colofabrix.scala.beesight.jumpevents.handlers

import cats.syntax.option.*
import com.colofabrix.scala.beesight.collections.NonEmptyWindow.NonEmptyFilled
import com.colofabrix.scala.beesight.config.FreefallConfig
import com.colofabrix.scala.beesight.jumpevents.model.WindowEntry
import com.colofabrix.scala.beesight.math.Stats
import java.time.Duration

/**
 * Detects freefall exit by identifying the onset of rapid vertical descent or acceleration.
 */
final private[handlers] class FreefallHandler private (config: FreefallConfig) extends EventHandler {

  protected def detectTrigger(window: NonEmptyFilled[WindowEntry]): Boolean =
    val kinematics = window.focus.kinematics

    val speedTrigger =
      kinematics.point.speed.vertical > config.verticalSpeedThreshold

    val accelerationTrigger =
      kinematics.acceleration > config.accelerationThreshold &&
      kinematics.point.speed.vertical > config.accelerationMinVelocity

    val isAboveMinAltitude =
      kinematics.point.altitude > config.minFreefallAltitude

    isAboveMinAltitude && (speedTrigger || accelerationTrigger)

  protected def validateTrigger(window: NonEmptyFilled[WindowEntry]): Boolean =
    val focus  = window.focus.kinematics
    val newest = window.newest.kinematics
    val dt     = Duration.between(focus.point.time, newest.point.time).toMillis / 1000.0

    val validationDistance = config.validationVerticalSpeed * dt

    newest.point.altitude < focus.point.altitude - validationDistance

  protected def findInflection(window: NonEmptyFilled[WindowEntry]): Option[Int] =
    val backWindow           = window.leftInclusive
    val discoveredInflection = Stats.findInflectionBy(backWindow)(_.kinematics.point.speed.vertical)

    discoveredInflection orElse window.focusIndex.some

}

private[handlers] object FreefallHandler {

  def apply(config: FreefallConfig): FreefallHandler =
    new FreefallHandler(config)

}
