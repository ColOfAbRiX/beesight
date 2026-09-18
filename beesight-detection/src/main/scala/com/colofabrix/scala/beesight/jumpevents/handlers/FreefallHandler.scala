package com.colofabrix.scala.beesight.jumpevents.handlers

import cats.syntax.option.*
import com.colofabrix.scala.beesight.collections.NonEmptyWindow.NonEmptyFilled
import com.colofabrix.scala.beesight.config.FreefallConfig
import com.colofabrix.scala.beesight.jumpevents.model.WindowEntry
import com.colofabrix.scala.beesight.math.Stats
import com.colofabrix.scala.beesight.model.*
import java.time.Duration

/**
 * Detects freefall exit by identifying the onset of rapid vertical descent or acceleration.
 */
final private[handlers] class FreefallHandler private (config: FreefallConfig) extends EventHandler {

  protected def detectTrigger(window: NonEmptyFilled[WindowEntry]): Boolean =
    val entry = window.focus

    val speedTrigger =
      entry.smoothedSpeed.vertical > config.verticalSpeedThreshold

    val accelerationTrigger =
      entry.acceleration > config.accelerationThreshold &&
      entry.smoothedSpeed.vertical > config.accelerationMinVelocity

    val isAboveMinAltitude =
      entry.point.altitude > config.minFreefallAltitude

    isAboveMinAltitude && (speedTrigger || accelerationTrigger)

  protected def validateTrigger(window: NonEmptyFilled[WindowEntry]): Boolean =
    val focus  = window.focus
    val newest = window.newest
    val dt     = Duration.between(focus.point.time, newest.point.time).toMillis / 1000.0

    val validationDistance = config.validationVerticalSpeed * dt

    newest.point.altitude < focus.point.altitude - validationDistance

  protected def findInflection(window: NonEmptyFilled[WindowEntry]): Option[Int] =
    val backWindow           = window.leftInclusive
    val discoveredInflection = Stats.findInflectionBy(backWindow)(_.smoothedSpeed.vertical)

    discoveredInflection orElse window.focusIndex.some

}

private[handlers] object FreefallHandler {

  def apply(config: FreefallConfig): FreefallHandler =
    new FreefallHandler(config)

}
