package com.colofabrix.scala.beesight.detection.jumpevents.handlers

import cats.syntax.option.*
import com.colofabrix.scala.beesight.detection.collections.NonEmptyWindow.NonEmptyFilled
import com.colofabrix.scala.beesight.detection.config.LandingConfig
import com.colofabrix.scala.beesight.detection.jumpevents.model.WindowEntry
import com.colofabrix.scala.beesight.detection.math.*
import com.colofabrix.scala.beesight.detection.model.*

/**
 * Detects landing by identifying sustained near-zero velocity and low speed variance.
 */
final private[handlers] class LandingHandler private (config: LandingConfig) extends EventHandler {

  protected def detectTrigger(window: NonEmptyFilled[WindowEntry]): Boolean =
    val entry = window.focus

    val isVerticalSpeedStable =
      window.stdDevBy(_.smoothedSpeed.vertical) < config.stabilityThreshold.value

    val speedTrigger =
      Math.abs(entry.smoothedSpeed.vertical) < config.verticalSpeedThreshold.value &&
      Math.abs(entry.smoothedSpeed.horizontal) < config.horizontalSpeedThreshold.value

    val canopyAltitude =
      window
        .focus
        .detectedEvents
        .canopy
        .fold(Double.MaxValue)(_.altitude)

    val isBelowCanopy = entry.point.altitude < canopyAltitude

    isVerticalSpeedStable && speedTrigger && isBelowCanopy

  protected def validateTrigger(window: NonEmptyFilled[WindowEntry]): Boolean =
    window
      .right
      .map { stabilityWindow =>
        stabilityWindow.stdDevBy(_.smoothedSpeed.vertical) < config.stabilityThreshold.value
      }
      .getOrElse(false)

  protected def findInflection(window: NonEmptyFilled[WindowEntry]): Option[Int] =
    window
      .focusIndex
      .some

}

private[handlers] object LandingHandler {

  def apply(config: LandingConfig): LandingHandler =
    new LandingHandler(config)

}
