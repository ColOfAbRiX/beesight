package com.colofabrix.scala.beesight.detection.stages

import com.colofabrix.scala.beesight.collections.NonEmptyWindow.NonEmptyFilled
import com.colofabrix.scala.beesight.config.CanopyConfig
import com.colofabrix.scala.beesight.detection.math.Stats
import com.colofabrix.scala.beesight.detection.model.{ DetectionData, IndexedDataPoint }
import com.colofabrix.scala.beesight.model.EventType
import java.time.Duration

final class CanopyHandler private (config: CanopyConfig) extends StageHandler {

  protected def detectTrigger(window: NonEmptyFilled[DetectionData]): Boolean =
    val kinematics = window.focus.kinematics

    val speedTrigger =
      kinematics.speed.vertical > 0.0 &&
      kinematics.speed.vertical < config.verticalSpeedThreshold

    val freefallAltitude =
      window
        .focus
        .detectedEvents
        .freefall
        .fold(100000.0)(_.altitude)

    val isBelowFreefall = kinematics.altitude < freefallAltitude

    val isAboveMinAltitude = kinematics.altitude > config.minCanopyAltitude

    speedTrigger && isBelowFreefall && isAboveMinAltitude

  protected def validateTrigger(window: NonEmptyFilled[DetectionData]): Boolean =
    val focus  = window.focus.kinematics
    val newest = window.newest.kinematics
    val dt     = Duration.between(focus.time, newest.time).toMillis / 1000.0

    val validationDistance = config.validationVerticalSpeed * dt

    newest.altitude < focus.altitude - validationDistance

  protected def findInflection(window: NonEmptyFilled[DetectionData]): Option[Int] =
    Some(window.focusIndex)

}

object CanopyHandler {

  def apply(config: CanopyConfig): CanopyHandler =
    new CanopyHandler(config)

}
