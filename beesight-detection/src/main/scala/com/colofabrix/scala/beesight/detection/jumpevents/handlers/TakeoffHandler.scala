package com.colofabrix.scala.beesight.detection.jumpevents.handlers

import cats.syntax.option.*
import com.colofabrix.scala.beesight.detection.collections.NonEmptyWindow.NonEmptyFilled
import com.colofabrix.scala.beesight.detection.config.TakeoffConfig
import com.colofabrix.scala.beesight.detection.jumpevents.model.WindowEntry
import com.colofabrix.scala.beesight.detection.math.*
import com.colofabrix.scala.beesight.detection.model.*
import java.time.Duration

/**
 * Detects takeoff by identifying the onset of climbing speed and horizontal movement.
 */
final private[handlers] class TakeoffHandler private (config: TakeoffConfig) extends EventHandler {

  protected def detectTrigger(window: NonEmptyFilled[WindowEntry]): Boolean =
    val entry = window.focus

    val speedTrigger =
      entry.smoothedSpeed.vertical < config.verticalSpeedThreshold.value &&
      entry.smoothedSpeed.horizontal > config.horizontalSpeedThreshold.value

    val isBelowMaxAltitude =
      entry.point.altitude < config.maxTakeoffAltitude.value

    isBelowMaxAltitude && speedTrigger

  protected def validateTrigger(window: NonEmptyFilled[WindowEntry]): Boolean =
    val focus  = window.focus
    val newest = window.newest
    val dt     = Duration.between(focus.point.time, newest.point.time).toMillis / 1000.0

    val validationDistance = config.validationVerticalSpeed.value * dt

    newest.point.altitude > focus.point.altitude - validationDistance

  protected def findInflection(window: NonEmptyFilled[WindowEntry]): Option[Int] =
    val discoveredInflection =
      for {
        backWindow <- window.left
        inflection <- backWindow.findInflectionBy(_.smoothedSpeed.vertical)
      } yield inflection

    discoveredInflection orElse window.focusIndex.some

}

private[handlers] object TakeoffHandler {

  def apply(config: TakeoffConfig): TakeoffHandler =
    new TakeoffHandler(config)

}
