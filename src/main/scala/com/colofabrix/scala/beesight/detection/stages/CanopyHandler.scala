package com.colofabrix.scala.beesight.detection.stages

import cats.syntax.option.*
import com.colofabrix.scala.beesight.collections.NonEmptyWindow.NonEmptyFilled
import com.colofabrix.scala.beesight.config.{CanopyConfig, FreefallConfig}
import com.colofabrix.scala.beesight.detection.DebugLog
import com.colofabrix.scala.beesight.detection.DebugLog.*
import com.colofabrix.scala.beesight.detection.math.Stats
import com.colofabrix.scala.beesight.detection.model.{ DetectionData, IndexedDataPoint }
import com.colofabrix.scala.beesight.model.EventType
import java.time.Duration

final class CanopyHandler private (config: CanopyConfig, freefallConfig: FreefallConfig) extends StageHandler {

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

    val canopyDistance = config.validationVerticalSpeed * dt
    val isBelowCanopy  = newest.altitude < focus.altitude - canopyDistance

    val freefallDistance = freefallConfig.verticalSpeedThreshold * dt
    val isAboveFreefall  = newest.altitude > focus.altitude - freefallDistance

    DebugLog(s"  |  canopyDistance=${canopyDistance.fmt}, isBelowCanopy=${isBelowCanopy}")
    DebugLog(s"  |  freefallDistance=${freefallDistance.fmt}, isAboveFreefall=$isAboveFreefall")

    isBelowCanopy && isAboveFreefall

  protected def findInflection(window: NonEmptyFilled[DetectionData]): Option[Int] =
    val discoveredInflection =
      for {
        backWindow  <- window.left
        speedsWindow = backWindow.map(_.kinematics.speed.vertical)
        inflection  <- Stats.findInflection(speedsWindow)
      } yield inflection

    discoveredInflection orElse window.focusIndex.some

}

object CanopyHandler {

  def apply(config: CanopyConfig, freefallConfig: FreefallConfig): CanopyHandler =
    new CanopyHandler(config, freefallConfig)

}
