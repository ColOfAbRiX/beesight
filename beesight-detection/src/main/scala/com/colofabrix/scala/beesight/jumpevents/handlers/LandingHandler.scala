package com.colofabrix.scala.beesight.jumpevents.handlers

import cats.syntax.option.*
import com.colofabrix.scala.beesight.collections.NonEmptyWindow.NonEmptyFilled
import com.colofabrix.scala.beesight.config.LandingConfig
import com.colofabrix.scala.beesight.jumpevents.model.WindowEntry
import com.colofabrix.scala.beesight.math.*
import com.colofabrix.scala.beesight.model.*

/**
 * Detects landing by identifying sustained near-zero velocity and low speed variance.
 */
final private[handlers] class LandingHandler private (config: LandingConfig) extends EventHandler {

  protected def detectTrigger(window: NonEmptyFilled[WindowEntry]): Boolean =
    val entry = window.focus

    val isVerticalSpeedStable =
      window.stdDevBy(_.smoothedSpeed.vertical) < config.stabilityThreshold

    val speedTrigger =
      Math.abs(entry.smoothedSpeed.vertical) < config.verticalSpeedThreshold &&
      Math.abs(entry.smoothedSpeed.horizontal) < config.horizontalSpeedThreshold

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
        stabilityWindow.stdDevBy(_.smoothedSpeed.vertical) < config.stabilityThreshold
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
