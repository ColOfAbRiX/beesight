package com.colofabrix.scala.beesight.detection.preprocess

import com.colofabrix.scala.beesight.collections.NonEmptyWindow.NonEmptyFilled
import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.detection.math.Interpolation
import java.time.Instant

/**
 * Detects and removes velocity spikes by interpolating outlier points.
 */
private[preprocess] object Despike {

  def despike(config: DetectionConfig, currentWindow: NonEmptyFilled[SpikeEntry]): NonEmptyFilled[SpikeEntry] =
    val oldest       = currentWindow.oldest
    val newest       = currentWindow.newest
    val focused      = currentWindow.focusAt(1)
    val secondOldest = focused.focus

    val expectedPoint = Interpolation.interpolate(oldest.point, newest.point, secondOldest.point.time)

    val actualVelocity   = secondOldest.point.speed.vertical
    val expectedVelocity = expectedPoint.speed.vertical
    val deviation        = Math.abs(actualVelocity - expectedVelocity)

    if deviation > config.global.accelerationClip then
      val interpolatedData = interpolate(oldest, newest, secondOldest.point.time)
      focused.modifyFocus(_ => interpolatedData)
    else
      focused

  private def interpolate(w1: SpikeEntry, w2: SpikeEntry, time: Instant): SpikeEntry =
    val interpolatedKinematics =
      for {
        k1    <- w1.kinematics
        k2    <- w2.kinematics
        result = Interpolation.interpolate(k1, k2, time)
      } yield result

    SpikeEntry(
      point = Interpolation.interpolate(w1.point, w2.point, time),
      kinematics = interpolatedKinematics,
    )

}
