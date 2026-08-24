package com.colofabrix.scala.beesight.detection.stages

import cats.syntax.option.*
import com.colofabrix.scala.beesight.collections.NonEmptyWindow.NonEmptyFilled
import com.colofabrix.scala.beesight.config.{ CanopyConfig, FreefallConfig }
import com.colofabrix.scala.beesight.detection.model.WindowEntry
import com.colofabrix.scala.beesight.math.Stats
import java.time.Duration

/**
 * Detects canopy deployment by identifying the transition from freefall to slower descent.
 */
final private[stages] class CanopyHandler private (config: CanopyConfig, freefallConfig: FreefallConfig)
  extends StageHandler {

  protected def detectTrigger(window: NonEmptyFilled[WindowEntry]): Boolean =
    val kinematics = window.focus.kinematics

    val speedTrigger =
      kinematics.speed.vertical > 0.0 &&
      kinematics.speed.vertical < config.verticalSpeedThreshold

    val accelerationTrigger =
      kinematics.acceleration.vertical < 0.0

    val freefallAltitude =
      window
        .focus
        .detectedEvents
        .freefall
        .fold(Double.MaxValue)(_.altitude)

    val isBelowFreefall = kinematics.altitude < freefallAltitude

    val isAboveMinAltitude = kinematics.altitude > config.minCanopyAltitude

    speedTrigger && accelerationTrigger && isBelowFreefall && isAboveMinAltitude

  protected def validateTrigger(window: NonEmptyFilled[WindowEntry]): Boolean =
    val focus  = window.focus.kinematics
    val newest = window.newest.kinematics
    val dt     = Duration.between(focus.time, newest.time).toMillis / 1000.0

    val canopyDistance = config.validationVerticalSpeed * dt
    val isBelowCanopy  = newest.altitude < focus.altitude - canopyDistance

    val freefallDistance = freefallConfig.verticalSpeedThreshold * dt
    val isAboveFreefall  = newest.altitude > focus.altitude - freefallDistance

    isBelowCanopy && isAboveFreefall

  protected def findInflection(window: NonEmptyFilled[WindowEntry]): Option[Int] =
    val discoveredInflection =
      for {
        backWindow <- window.left
        inflection <- Stats.findInflectionBy(backWindow)(_.kinematics.speed.vertical)
      } yield inflection

    discoveredInflection orElse window.focusIndex.some

}

private[stages] object CanopyHandler {

  def apply(config: CanopyConfig, freefallConfig: FreefallConfig): CanopyHandler =
    new CanopyHandler(config, freefallConfig)

}
