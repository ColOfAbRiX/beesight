package com.colofabrix.scala.beesight.detection.stages

import cats.syntax.option.*
import com.colofabrix.scala.beesight.collections.NonEmptyWindow.NonEmptyFilled
import com.colofabrix.scala.beesight.config.LandingConfig
import com.colofabrix.scala.beesight.detection.model.WindowEntry
import com.colofabrix.scala.beesight.math.Stats

/**
 * Detects landing by identifying sustained near-zero velocity and low speed variance.
 */
final private[stages] class LandingHandler private (config: LandingConfig) extends StageHandler {

  protected def detectTrigger(window: NonEmptyFilled[WindowEntry]): Boolean =
    val kinematics = window.focus.kinematics

    val isVerticalSpeedStable =
      Stats.stdDevBy(window)(_.kinematics.speed.vertical) < config.stabilityThreshold

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

    isVerticalSpeedStable && speedTrigger && isBelowCanopy

  protected def validateTrigger(window: NonEmptyFilled[WindowEntry]): Boolean =
    window
      .right
      .map { stabilityWindow =>
        Stats.stdDevBy(stabilityWindow)(_.kinematics.speed.vertical) < config.stabilityThreshold
      }
      .getOrElse(false)

  protected def findInflection(window: NonEmptyFilled[WindowEntry]): Option[Int] =
    window
      .focusIndex
      .some

}

private[stages] object LandingHandler {

  def apply(config: LandingConfig): LandingHandler =
    new LandingHandler(config)

}
