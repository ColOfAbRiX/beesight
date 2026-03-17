package com.colofabrix.scala.beesight.detection.stages

import cats.syntax.option.*
import com.colofabrix.scala.beesight.collections.NonEmptyWindow.NonEmptyFilled
import com.colofabrix.scala.beesight.config.TakeoffConfig
import com.colofabrix.scala.beesight.detection.math.Stats
import com.colofabrix.scala.beesight.detection.model.DetectionData
import java.time.Duration

/**
 * Detects takeoff by identifying the onset of climbing speed and horizontal movement.
 */
final private[stages] class TakeoffHandler private (config: TakeoffConfig) extends StageHandler {

  protected def detectTrigger(window: NonEmptyFilled[DetectionData]): Boolean =
    val kinematics = window.focus.kinematics

    val speedTrigger =
      kinematics.speed.vertical < config.verticalSpeedThreshold &&
      kinematics.speed.horizontal > config.horizontalSpeedThreshold

    val isBelowMaxAltitude =
      kinematics.altitude < config.maxTakeoffAltitude

    isBelowMaxAltitude && speedTrigger

  protected def validateTrigger(window: NonEmptyFilled[DetectionData]): Boolean =
    val focus  = window.focus.kinematics
    val newest = window.newest.kinematics
    val dt     = Duration.between(focus.time, newest.time).toMillis / 1000.0

    val validationDistance = config.validationVerticalSpeed * dt

    newest.altitude > focus.altitude - validationDistance

  protected def findInflection(window: NonEmptyFilled[DetectionData]): Option[Int] =
    val discoveredInflection =
      for {
        backWindow <- window.left
        inflection <- Stats.findInflectionBy(backWindow)(_.kinematics.speed.vertical)
      } yield inflection

    discoveredInflection orElse window.focusIndex.some

}

private[stages] object TakeoffHandler {

  def apply(config: TakeoffConfig): TakeoffHandler =
    new TakeoffHandler(config)

}
