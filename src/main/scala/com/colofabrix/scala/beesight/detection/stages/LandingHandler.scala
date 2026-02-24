package com.colofabrix.scala.beesight.detection.stages

import com.colofabrix.scala.beesight.collections.NonEmptyWindow.NonEmptyFilled
import com.colofabrix.scala.beesight.config.LandingConfig
import com.colofabrix.scala.beesight.detection.math.Stats
import com.colofabrix.scala.beesight.detection.model.{ DetectionData, IndexedDataPoint }
import com.colofabrix.scala.beesight.model.EventType
import java.time.Duration
import scala.annotation.nowarn

final class LandingHandler private (@nowarn config: LandingConfig) extends StageHandler {

  protected def detectTrigger(window: NonEmptyFilled[DetectionData]): Boolean =
    val kinematics = window.focus.kinematics

    val isVerticalSpeedStable =
      Stats.stdDev(window.map(_.kinematics.speed.vertical)) < config.stabilityThreshold

    val speedTrigger =
      Math.abs(kinematics.speed.vertical) < config.verticalSpeedThreshold &&
      Math.abs(kinematics.speed.horizontal) < config.horizontalSpeedThreshold

    val canopyAltitude =
      window
        .focus
        .detectedEvents
        .canopy
        .fold(100000.0)(_.altitude)

    val isBelowCanopy = kinematics.altitude < canopyAltitude

    isVerticalSpeedStable && speedTrigger && isBelowCanopy

  protected def validateTrigger(window: NonEmptyFilled[DetectionData]): Boolean =
    val focus  = window.focus.kinematics
    val newest = window.newest.kinematics
    val dt     = Duration.between(focus.time, newest.time).toMillis / 1000.0

    val validationDistance = config.validationVerticalSpeed * dt

    newest.altitude < focus.altitude - validationDistance

  protected def findInflection(window: NonEmptyFilled[DetectionData]): Option[Int] =
    Some(window.focusIndex)

}

object LandingHandler {

  def apply(config: LandingConfig): LandingHandler =
    new LandingHandler(config)

}
