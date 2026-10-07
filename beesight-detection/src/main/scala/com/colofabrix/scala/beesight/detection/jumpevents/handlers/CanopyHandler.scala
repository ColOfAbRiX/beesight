package com.colofabrix.scala.beesight.detection.jumpevents.handlers

import cats.syntax.option.*
import com.colofabrix.scala.beesight.detection.collections.NonEmptyWindow.NonEmptyFilled
import com.colofabrix.scala.beesight.detection.config.{ CanopyConfig, FreefallConfig }
import com.colofabrix.scala.beesight.detection.jumpevents.model.WindowEntry
import com.colofabrix.scala.beesight.detection.math.*
import java.time.Duration

/**
 * Detects canopy deployment by identifying the transition from freefall to slower descent.
 */
final private[handlers] class CanopyHandler private (config: CanopyConfig, freefallConfig: FreefallConfig)
  extends EventHandler {

  protected def detectTrigger(window: NonEmptyFilled[WindowEntry]): Boolean =
    val entry = window.focus

    val speedTrigger =
      entry.smoothedSpeed.vertical > 0.0 &&
      entry.smoothedSpeed.vertical < config.verticalSpeedThreshold.value

    val accelerationTrigger =
      entry.acceleration.vertical < 0.0

    val freefallAltitude =
      window
        .focus
        .detectedEvents
        .freefall
        .fold(Double.MaxValue)(_.altitude)

    val isBelowFreefall = entry.point.altitude < freefallAltitude

    val isAboveMinAltitude = entry.point.altitude > config.minCanopyAltitude.value

    speedTrigger && accelerationTrigger && isBelowFreefall && isAboveMinAltitude

  protected def validateTrigger(window: NonEmptyFilled[WindowEntry]): Boolean =
    val focus  = window.focus
    val newest = window.newest
    val dt     = Duration.between(focus.point.time, newest.point.time).toMillis / 1000.0

    val canopyDistance = config.validationVerticalSpeed.value * dt
    val isBelowCanopy  = newest.point.altitude < focus.point.altitude - canopyDistance

    val freefallDistance = freefallConfig.verticalSpeedThreshold.value * dt
    val isAboveFreefall  = newest.point.altitude > focus.point.altitude - freefallDistance

    isBelowCanopy && isAboveFreefall

  protected def findInflection(window: NonEmptyFilled[WindowEntry]): Option[Int] =
    val discoveredInflection =
      for {
        backWindow <- window.left
        inflection <- backWindow.findInflectionBy(_.smoothedSpeed.vertical)
      } yield inflection

    discoveredInflection orElse window.focusIndex.some

}

private[handlers] object CanopyHandler {

  def apply(config: CanopyConfig, freefallConfig: FreefallConfig): CanopyHandler =
    new CanopyHandler(config, freefallConfig)

}
