package com.colofabrix.scala.beesight.preprocess

import com.colofabrix.scala.beesight.collections.NonEmptyWindow.NonEmptyFilled
import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.math.Interpolation
import com.colofabrix.scala.beesight.model.PointKinematics
import java.time.Instant

/**
 * Detects and removes velocity spikes by interpolating outlier points.
 */
private[preprocess] object SpeedClipper {

  def clip(config: DetectionConfig, window: NonEmptyFilled[PointKinematics]): NonEmptyFilled[PointKinematics] =
    val oldest       = window.oldest
    val newest       = window.newest
    val focused      = window.focusAt(1)
    val secondOldest = focused.focus

    val expectedPoint = Interpolation.interpolate(oldest, newest, secondOldest.point.time)

    val actualVelocity   = secondOldest.point.speed.vertical
    val expectedVelocity = expectedPoint.point.speed.vertical
    val deviation        = Math.abs(actualVelocity - expectedVelocity)

    if deviation <= config.global.accelerationClip then
      focused
    else
      focused.modifyFocus(_ => expectedPoint)

}
