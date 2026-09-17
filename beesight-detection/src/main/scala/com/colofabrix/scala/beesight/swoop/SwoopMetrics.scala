package com.colofabrix.scala.beesight.swoop

/**
 * Computation of the swoop metrics: the maximum speeds over the detection windows and the total turn angle.
 */
private[swoop] object SwoopMetrics {

  /** A maximum-speed scan result: the speed value and the index of the point achieving it. */
  final private[swoop] case class MaxSpeed(speed: Double, index: Int)

  /** The maximum speeds over the swoop detection windows. */
  final private[swoop] case class MaxSpeeds(
    vertical: MaxSpeed,
    total: MaxSpeed,
    horizontal: MaxSpeed,
  )

  /**
   * Maximum speeds over the detection windows: vertical and total speed over [top of turn, stop), horizontal speed
   * over [rollout, stop). Degenerate windows fall back to the dive point, whose descent is strictly positive by
   * construction.
   */
  def maxSpeeds(series: PointSeries, diveIdx: Int, topOfTurn: Int, rolloutIdx: Int, stopIdx: Int): MaxSpeeds = {
    val vertical   = argmax(topOfTurn, stopIdx)(series.verticalSpeed)
    val total      = argmax(topOfTurn, stopIdx)(series.totalSpeed)
    val horizontal = argmax(rolloutIdx, stopIdx)(series.horizontalSpeed)

    MaxSpeeds(
      vertical = vertical.map(i => MaxSpeed(series.verticalSpeed(i), i)).getOrElse(MaxSpeed(
        series.verticalSpeed(diveIdx),
        diveIdx
      )),
      total = total.map(i => MaxSpeed(series.totalSpeed(i), i)).getOrElse(MaxSpeed(series.totalSpeed(diveIdx), diveIdx)),
      horizontal = horizontal
        .map(i => MaxSpeed(series.horizontalSpeed(i), i))
        .getOrElse(MaxSpeed(series.horizontalSpeed(rolloutIdx), rolloutIdx)),
    )
  }

  /**
   * First-max scan over the half-open window [from, until): the earliest point achieving the maximum of `f` among
   * strictly positive values. Values at or below zero never qualify.
   */
  private def argmax(from: Int, until: Int)(f: Int => Double): Option[Int] = {
    var bestIdx = -1
    var best    = 0.0

    for i <- from until until do {
      val value = f(i)
      if best < value then {
        bestIdx = i
        best = value
      }
    }

    Option.when(bestIdx >= 0)(bestIdx)
  }

  /**
   * The signed sum of the per-sample turn angles over (top of turn, bottom of turn], skipping values of a full turn
   * or more. The angles come from the unwrap pass.
   */
  def turnDegrees(series: PointSeries, topOfTurn: Int, bottomIdx: Int): Double = {
    val turnAngles = turnAnglesWithUnwrap(series)

    var total = 0.0
    for i <- topOfTurn + 1 to bottomIdx do {
      val turnAngle = turnAngles(i)
      if turnAngle < SwoopConstants.fullTurnAngle then total += turnAngle
    }

    total
  }

  /**
   * Per-sample turn angles (heading deltas wrapped to [-180, 180]) with segment tracking. Inside a steep fast dive, a
   * run of samples whose turn direction is opposite to the established one opens a segment; while it lasts (the
   * opposite state persists, the dive persists, and the track stays within the displacement cap of the segment start),
   * the segment's angles are replaced by dt-weighted shares of the net heading change from the sample before the
   * segment to its end, resolved into the pre-segment direction and only when the resolved magnitude is under the
   * segment cap. The displacement test is dead-reckoned from the horizontal velocities (the stream carries no
   * positions).
   */
  private def turnAnglesWithUnwrap(series: PointSeries): Vector[Double] = {
    val n       = series.length
    val turn    = Array.fill(n)(0.0)
    val heading = (0 until n).map(series.heading).toArray
    val timeMs  = (0 until n).map(series.timeMillis).toArray

    if n >= 2 then {
      var prev     = 2 // 1 = right run, 0 = left run, 2 = straight
      var segStart = 0 // 0 = no open segment
      var segX     = 0.0
      var segY     = 0.0

      for i <- 1 until n do {
        val d = wrapDegrees(heading(i) - heading(i - 1))
        turn(i) = d

        val inDive = diveAngle(series, i) > SwoopConstants.diveAngleCap &&
          series.verticalSpeed(i) > SwoopConstants.diveVerticalSpeed
        val state   = turnDirection(d)
        val flipped = (prev == 1 && state == 0) || (prev == 0 && state == 1)

        if segStart == 0 then {
          if flipped && inDive then {
            segStart = i
            segX = 0.0
            segY = 0.0
          }
        } else {
          val dtSec = (timeMs(i) - timeMs(i - 1)) / 1000.0
          segX += series.northSpeed(i) * dtSec
          segY += series.eastSpeed(i) * dtSec

          val segmentEnded = !flipped || !inDive || Math.hypot(segX, segY) > SwoopConstants.segmentDisplacementCap

          if segmentEnded then {
            resolveSegment(turn, heading, timeMs, segStart, i - 1, prev)
            segStart = 0
          }
        }

        if segStart == 0 && state != 2 then prev = state
      }
    }

    turn.toVector
  }

  /** The dive angle at point `i`: the arctangent of the vertical speed over the horizontal speed, in degrees; +/-90 when there is no horizontal speed. */
  private def diveAngle(series: PointSeries, i: Int): Double = {
    val h        = series.horizontalSpeed(i)
    val vertical = series.verticalSpeed(i)
    if h == 0.0 then
      if vertical < 0.0 then -90.0 else 90.0
    else
      Math.atan(vertical / h) * 180.0 / Math.PI
  }

  /** Wraps a heading delta to [-180, 180]. */
  private def wrapDegrees(raw: Double): Double =
    if raw > 180.0 then raw - 360.0 else if raw < -180.0 then raw + 360.0 else raw

  /** The turn direction of a per-sample angle: 1 = right run, 0 = left run, 2 = straight. */
  private def turnDirection(d: Double): Int =
    if d > SwoopConstants.turnStateThreshold then 1 else if d < -SwoopConstants.turnStateThreshold then 0 else 2

  /**
   * Replaces the angles of a closed segment with dt-weighted shares of the net heading change from the sample before
   * the segment to its end, resolved into the pre-segment direction and only when the resolved magnitude is under the
   * segment cap.
   */
  private def resolveSegment(
    turn: Array[Double],
    heading: Array[Double],
    timeMs: Array[Double],
    segStart: Int,
    end: Int,
    direction: Int
  ): Unit = {
    var total = heading(end) - heading(segStart - 1)
    if total < 0.0 && direction == 1 then total += 360.0
    if total > 0.0 && direction == 0 then total -= 360.0

    if Math.abs(total) < SwoopConstants.segmentAngleCap then {
      val rate = 1000.0 * total / (timeMs(end) - timeMs(segStart - 1))
      for k <- segStart to end do {
        turn(k) = (timeMs(k) - timeMs(k - 1)) * rate / 1000.0
      }
    }
  }

}
