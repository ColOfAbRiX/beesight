package com.colofabrix.scala.beesight.swoop.computations

import com.colofabrix.scala.beesight.config.SwoopConfig
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
   * The carve is the maximal interval around the dive point where the smoothed turn rate stays above the carve exit
   * threshold in the dominant direction, considering only samples whose horizontal speed makes the GPS heading
   * reliable and tolerating brief gaps. The dominant direction comes from the net heading change over the samples
   * surrounding the dive point. The carve exists only if it contains rotation above the carve entry threshold: the
   * high entry bar keeps slow non-swoop rotations out, while the low exit bar keeps the decaying tail of a real turn
   * inside the window. When the interval grows past the physical size of a swoop turn it is absorbing drift outside
   * the turn, and the plain entry-threshold carve is reported instead. When no sustained fast rotation exists - a
   * straight dive, a slow landing-pattern turn, or heading noise at low horizontal speed - there is no carve and the
   * turn angle is zero.
   *
   * This replaces the sum of the per-sample heading deltas over (top of turn, bottom of turn],
   * which accumulates heading noise at low horizontal speeds, is cancelled by post-turn S-turns inside the window,
   * and counts slow non-swoop rotations such as the landing pattern as turn degrees.
   */
  def turnDegrees(series: PointSeries, topOfTurn: Int, endIdx: Int, diveIdx: Int, config: SwoopConfig): Double = {
    val n = series.length

    /** Per-sample turn rate in deg/s, defined only where both samples carry a reliable heading. */
    def rate(i: Int): Option[Double] =
      if i <= 0 || i >= n then
        None
      else {
        val dtSec = (series.timeMillis(i) - series.timeMillis(i - 1)) / 1000.0

        val speed     = series.horizontalSpeed(i)
        val speedPrev = series.horizontalSpeed(i - 1)

        if dtSec <= 0 then
          None
        else if speed < config.carve.reliableSpeed.value || speedPrev < config.carve.reliableSpeed.value then
          None
        else
          Some(wrapDegrees(series.heading(i) - series.heading(i - 1)) / dtSec)
      }

    /** Turn rate smoothed over a +/-2 sample window. */
    def smoothRate(i: Int): Option[Double] =
      val values = (i - 2 to i + 2).flatMap(rate)
      if values.isEmpty then None
      else Some(values.sum / values.size)

    //  Dominant direction: the net heading change over +/-2 s of reliable samples around the dive point  //
    val backRef = (diveIdx - config.carve.directionSamples.value to topOfTurn by -1)
      .find(i => series.horizontalSpeed(i) >= config.carve.reliableSpeed.value)
      .getOrElse((diveIdx - config.carve.directionSamples.value) max topOfTurn)

    val fwdRef = (diveIdx + config.carve.directionSamples.value to endIdx)
      .find(i => series.horizontalSpeed(i) >= config.carve.reliableSpeed.value)
      .getOrElse((diveIdx + config.carve.directionSamples.value) min endIdx)

    val aroundNet = wrapDegrees(series.heading(fwdRef) - series.heading(backRef))

    if Math.abs(aroundNet) < config.carve.minDirectionChange.value then
      return 0.0

    val dir = if aroundNet > 0 then 1 else -1

    //  Expand forward from the dive point while the rotation persists: fast core first, then the decaying tail  //

    var end      = diveIdx
    var endBase  = diveIdx
    var misses   = 0
    var missesB  = 0
    var core     = false
    var baseDone = false
    var exitRun  = 0
    var i        = diveIdx + 1

    /** Any sample that does not extend the plain carve is a miss for it. */
    def baselineMiss(): Unit =
      if !baseDone then
        missesB += 1
        if missesB > config.carve.gapSamples.value then baseDone = true

    while i <= endIdx do {
      smoothRate(i) match
        case Some(r) if r * dir > config.carve.rateThreshold.value =>
          end = i
          core = true
          misses = 0
          exitRun = 0
          if !baseDone then { endBase = i; missesB = 0 }
        case Some(r) if core && r * dir > config.carve.exitRateThreshold.value =>
          misses = 0
          baselineMiss()
          exitRun += 1
          if exitRun >= 2 then end = i
        case _ =>
          misses += 1
          exitRun = 0
          baselineMiss()
          if misses > config.carve.gapSamples.value then { end = i - misses; i = endIdx + 1 }
      i += 1
    }

    val hasCore = core

    //  Expand backward from the dive point while the rotation persists  //

    var start     = diveIdx
    var startBase = diveIdx

    misses = 0
    missesB = 0
    core = false
    baseDone = false
    exitRun = 0
    i = diveIdx

    while i > topOfTurn + 1 do {
      smoothRate(i) match
        case Some(r) if r * dir > config.carve.rateThreshold.value =>
          start = i - 1
          core = true
          misses = 0
          exitRun = 0
          if !baseDone then { startBase = i - 1; missesB = 0 }
        case Some(r) if core && r * dir > config.carve.exitRateThreshold.value =>
          misses = 0
          baselineMiss()
          exitRun += 1
          if exitRun >= 2 then start = i - 1
        case _ =>
          misses += 1
          exitRun = 0
          baselineMiss()
          if misses > config.carve.gapSamples.value then { start = i + misses - 1; i = 0 }
      i -= 1
    }

    //  A carve must contain a sample rotating faster than the entry threshold, else it is not a carve  //
    if !(hasCore || core) then
      return 0.0

    //  The turn angle: the signed sum of the per-sample deltas over the carve  //
    var total = 0.0
    for i <- start + 1 to end do total += wrapDegrees(series.heading(i) - series.heading(i - 1))

    //  An expansion past the physical turn size is absorbing drift outside the turn: report the plain carve  //
    if Math.abs(total) > config.carve.maxTurnDegrees.value then
      total = 0.0
      for i <- startBase + 1 to endBase do 
        total += wrapDegrees(series.heading(i) - series.heading(i - 1))

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
