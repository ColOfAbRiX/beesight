package com.colofabrix.scala.beesight.detection.stages

import cats.syntax.option.*
import com.colofabrix.scala.beesight.collections.NonEmptyWindow.NonEmptyFilled
import com.colofabrix.scala.beesight.config.LandingConfig
import com.colofabrix.scala.beesight.detection.math.Stats
import com.colofabrix.scala.beesight.detection.model.{ DetectionData, IndexedDataPoint }
import com.colofabrix.scala.beesight.model.EventType
import java.time.Duration

final class LandingHandler private (config: LandingConfig) extends StageHandler {

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
        .fold(Double.MaxValue)(_.altitude)

    val isBelowCanopy = kinematics.altitude < canopyAltitude

    val stdev = Stats.stdDev(window.map(_.kinematics.speed.vertical))

    isVerticalSpeedStable && speedTrigger && isBelowCanopy

  protected def validateTrigger(window: NonEmptyFilled[DetectionData]): Boolean =
    window
      .right
      .map { stabilityWindow =>
        val speeds = stabilityWindow.map(_.kinematics.speed.vertical)
        Stats.stdDev(speeds) < config.stabilityThreshold
      }
      .getOrElse(false)

  protected def findInflection(window: NonEmptyFilled[DetectionData]): Option[Int] =
    window
      .focusIndex
      .some

}

object LandingHandler {

  def apply(config: LandingConfig): LandingHandler =
    new LandingHandler(config)

}
