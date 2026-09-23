package com.colofabrix.scala.beesight.swoop

import com.colofabrix.scala.beesight.swoop.model.PointSeries

/**
 * Computation of the swoop metrics: the maximum speeds over the detection windows and the total turn angle.
 */
private[swoop] object SwoopMetrics {

  /** A maximum-speed scan result: the speed value and the index of the point achieving it. */
  final case class MaxSpeed(
    speed: Double,
    index: Int
  )

  /** The maximum speeds over the swoop detection windows. */
  final case class MaxSpeeds(
    total: MaxSpeed,
    vertical: MaxSpeed,
    horizontal: MaxSpeed,
  )

  /**
   * Maximum speeds over the detection windows: vertical and total speed over [top of turn, stop), horizontal speed
   * over [rollout, stop). Degenerate windows fall back to the dive point, whose descent is strictly positive by
   * construction.
   */
  def maxSpeeds(series: PointSeries, diveIdx: Int, topOfTurn: Int, rolloutIdx: Int, stopIdx: Int): MaxSpeeds = {
    val maxTotalSpeed =
      argmax(topOfTurn, stopIdx)(series.totalSpeed)
        .map { i =>
          MaxSpeed(series.totalSpeed(i), i)
        }
        .getOrElse {
          MaxSpeed(series.totalSpeed(diveIdx), diveIdx)
        }

    val maxVerticalSpeed =
      argmax(topOfTurn, stopIdx)(series.verticalSpeed)
        .map { i =>
          MaxSpeed(series.verticalSpeed(i), i)
        }
        .getOrElse {
          MaxSpeed(series.verticalSpeed(diveIdx), diveIdx)
        }

    val maxHorizontalSpeed =
      argmax(rolloutIdx, stopIdx)(series.horizontalSpeed)
        .map { i =>
          MaxSpeed(series.horizontalSpeed(i), i)
        }
        .getOrElse {
          MaxSpeed(series.horizontalSpeed(rolloutIdx), rolloutIdx)
        }

    MaxSpeeds(maxTotalSpeed, maxVerticalSpeed, maxHorizontalSpeed)
  }

  /**
   * First-max scan over the half-open window [from, until): the earliest point achieving the maximum of `f` among
   * strictly positive values. Values at or below zero never qualify.
   */
  private def argmax(from: Int, until: Int)(f: Int => Double): Option[Int] = (from until until)
    .view
    .map(i => f(i) -> i)
    .filter(_._1 > 0)
    .maxByOption(_._1)
    .map(_._2)

  /**
   * The turn angle: the net heading change over the carve, the sustained rotation around the dive point.
   *
   * The carve is the maximal interval around the dive point where the smoothed turn rate stays above the carve
   * threshold in the dominant direction, considering only samples whose horizontal speed makes the GPS heading
   * reliable and tolerating brief gaps. The dominant direction comes from the net heading change over the samples
   * surrounding the dive point. When no sustained fast rotation exists - a straight dive, a slow landing-pattern
   * turn, or heading noise at low horizontal speed - there is no carve and the turn angle is zero.
   *
   * This replaces the sum of the per-sample heading deltas over (top of turn, bottom of turn],
   * which accumulates heading noise at low horizontal speeds, is cancelled by post-turn S-turns inside the window,
   * and counts slow non-swoop rotations such as the landing pattern as turn degrees.
   */
  def turnDegrees(series: PointSeries, topOfTurn: Int, bottomIdx: Int, diveIdx: Int): Double = {
    val n = series.length

    /** Per-sample turn rate in deg/s, defined only where both samples carry a reliable heading. */
    def rate(i: Int): Option[Double] =
      if i <= 0 || i >= n then None
      else {
        val dtSec = (series.timeMillis(i) - series.timeMillis(i - 1)) / 1000.0
        if dtSec <= 0 then None
        else if series.horizontalSpeed(i) < SwoopConstants.carveReliableSpeed ||
          series.horizontalSpeed(i - 1) < SwoopConstants.carveReliableSpeed
        then None
        else Some(wrapDegrees(series.heading(i) - series.heading(i - 1)) / dtSec)
      }

    /** Turn rate smoothed over a +/-2 sample window. */
    def smoothRate(i: Int): Option[Double] =
      val values = (i - 2 to i + 2).flatMap(rate)
      if values.isEmpty then None
      else Some(values.sum / values.size)

    //  Dominant direction: the net heading change over +/-2 s of reliable samples around the dive point  //
    val backRef = (diveIdx - SwoopConstants.carveDirectionSamples to topOfTurn by -1)
      .find(i => series.horizontalSpeed(i) >= SwoopConstants.carveReliableSpeed)
      .getOrElse((diveIdx - SwoopConstants.carveDirectionSamples) max topOfTurn)

    val fwdRef = (diveIdx + SwoopConstants.carveDirectionSamples to bottomIdx)
      .find(i => series.horizontalSpeed(i) >= SwoopConstants.carveReliableSpeed)
      .getOrElse((diveIdx + SwoopConstants.carveDirectionSamples) min bottomIdx)

    val aroundNet = wrapDegrees(series.heading(fwdRef) - series.heading(backRef))
    if Math.abs(aroundNet) < SwoopConstants.carveMinDirectionChange then return 0.0
    val dir = if aroundNet > 0 then 1 else -1

    //  Expand forward from the dive point while the rotation persists  //
    var end    = diveIdx
    var misses = 0
    var i      = diveIdx + 1
    while i <= bottomIdx do
      smoothRate(i) match
        case Some(r) if r * dir > SwoopConstants.carveRateThreshold =>
          end = i
          misses = 0
        case _ =>
          misses += 1
          if misses > SwoopConstants.carveGapSamples then { end = i - misses; i = bottomIdx + 1 }
      i += 1

    //  Expand backward from the dive point while the rotation persists  //
    var start = diveIdx
    misses = 0
    i = diveIdx
    while i > topOfTurn + 1 do
      smoothRate(i) match
        case Some(r) if r * dir > SwoopConstants.carveRateThreshold =>
          start = i - 1
          misses = 0
        case _ =>
          misses += 1
          if misses > SwoopConstants.carveGapSamples then { start = i + misses - 1; i = 0 }
      i -= 1

    //  A carve must contain a sample rotating faster than the threshold, else it is not a carve  //
    val hasCore = (start to end).exists { k =>
      smoothRate(k).exists(r => r * dir > SwoopConstants.carveRateThreshold)
    }
    if !hasCore then return 0.0

    //  The turn angle: the signed sum of the per-sample deltas over the carve  //
    var total = 0.0
    for i <- start + 1 to end do total += wrapDegrees(series.heading(i) - series.heading(i - 1))
    total
  }

  /** Wraps a heading delta to [-180, 180]. */
  private def wrapDegrees(raw: Double): Double =
    if raw > 180.0 then
      raw - 360.0
    else if raw < -180.0 then
      raw + 360.0
    else
      raw

}
