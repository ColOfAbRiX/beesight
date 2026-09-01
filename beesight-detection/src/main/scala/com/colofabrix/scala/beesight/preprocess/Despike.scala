package com.colofabrix.scala.beesight.preprocess

import com.colofabrix.scala.beesight.collections.NonEmptyWindow.NonEmptyFilled
import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.math.Interpolation
import com.colofabrix.scala.beesight.model.PointKinematics
import java.time.Instant

/**
 * Detects and removes velocity spikes by interpolating outlier points.
 */
private[preprocess] object Despike {

  def despike(
    config: DetectionConfig,
    currentWindow: NonEmptyFilled[PointKinematics]
  ): NonEmptyFilled[PointKinematics] =
    val oldest       = currentWindow.oldest
    val newest       = currentWindow.newest
    val focused      = currentWindow.focusAt(1)
    val secondOldest = focused.focus

    val expectedPoint = Interpolation.interpolate(oldest.point, newest.point, secondOldest.point.time)

    val actualVelocity   = secondOldest.point.speed.vertical
    val expectedVelocity = expectedPoint.speed.vertical
    val deviation        = Math.abs(actualVelocity - expectedVelocity)

    if deviation <= config.global.accelerationClip then
      focused
    else
      focused.modifyFocus { _ =>
        Interpolation.interpolate(oldest, newest, secondOldest.point.time)
      }

}
