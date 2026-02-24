package com.colofabrix.scala.beesight.detection.stages

import com.colofabrix.scala.beesight.collections.NonEmptyWindow.NonEmptyFilled
import com.colofabrix.scala.beesight.config.CanopyConfig
import com.colofabrix.scala.beesight.detection.DebugLog
import com.colofabrix.scala.beesight.detection.DebugLog.*
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
        .fold(Double.MaxValue)(_.altitude)

    val isBelowFreefall = kinematics.altitude < freefallAltitude

    val isAboveMinAltitude = kinematics.altitude > config.minCanopyAltitude

    DebugLog(s"  |  altitude=${kinematics.altitude.fmt}, verticalSpeed=${kinematics.speed.vertical.fmt}")
    DebugLog(s"  |  speedTrigger=$speedTrigger, isBelowFreefall=$isBelowFreefall, isAboveMinAltitude=$isAboveMinAltitude")

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
