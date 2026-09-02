package com.colofabrix.scala.beesight.jumpevents.handlers

import cats.syntax.option.*
import com.colofabrix.scala.beesight.collections.NonEmptyWindow.NonEmptyFilled
import com.colofabrix.scala.beesight.config.TakeoffConfig
import com.colofabrix.scala.beesight.jumpevents.model.WindowEntry
import com.colofabrix.scala.beesight.math.Stats
import java.time.Duration

/**
 * Detects takeoff by identifying the onset of climbing speed and horizontal movement.
 */
final private[handlers] class TakeoffHandler private (config: TakeoffConfig) extends EventHandler {

  protected def detectTrigger(window: NonEmptyFilled[WindowEntry]): Boolean =
    val kinematics = window.focus.kinematics

    val speedTrigger =
      kinematics.point.speed.vertical < config.verticalSpeedThreshold &&
      kinematics.point.speed.horizontal > config.horizontalSpeedThreshold

    val isBelowMaxAltitude =
      kinematics.point.altitude < config.maxTakeoffAltitude

    isBelowMaxAltitude && speedTrigger

  protected def validateTrigger(window: NonEmptyFilled[WindowEntry]): Boolean =
    val focus  = window.focus.kinematics
    val newest = window.newest.kinematics
    val dt     = Duration.between(focus.point.time, newest.point.time).toMillis / 1000.0

    val validationDistance = config.validationVerticalSpeed * dt

    newest.point.altitude > focus.point.altitude - validationDistance

  protected def findInflection(window: NonEmptyFilled[WindowEntry]): Option[Int] =
    val discoveredInflection =
      for {
        backWindow <- window.left
        inflection <- Stats.findInflectionBy(backWindow)(_.kinematics.point.speed.vertical)
      } yield inflection

    discoveredInflection orElse window.focusIndex.some

}

private[handlers] object TakeoffHandler {

  def apply(config: TakeoffConfig): TakeoffHandler =
    new TakeoffHandler(config)

}
