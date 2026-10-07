package com.colofabrix.scala.beesight.detection.swoop.computations

import com.colofabrix.scala.beesight.detection.config.SwoopConfig
import com.colofabrix.scala.beesight.detection.swoop.model.PointSeries

/**
 * Computation of the swoop metrics: the maximum speeds over the detection windows and the total turn angle.
 */
private[swoop] object SwoopMetrics {

  /** A maximum speed over a detection window, with the index of the point where it occurs. */
  final case class MaxSpeed(
    speed: Double,
    index: Int
  )

  /** Maximum total, vertical and horizontal speeds, each with its point index. */
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
   * The signed sum of the per-sample turn angles over (top of turn, bottom of turn], skipping values of a full turn
   * or more. The angles come from the segment-resolved per-sample rotation. When the trim is enabled, both window
   * ends are trimmed over the drift: samples rotating at or below the trim threshold are not part of the turn, and
   * the trims share one net-shift budget, stopping when the combined shift reaches the trim cap.
   */
  def turnDegrees(series: PointSeries, topOfTurn: Int, bottomIdx: Int, config: SwoopConfig): Double = {
    val turnAngles = turnAnglesWithSegments(series, config)
    val rotation   = config.rotation

    var start = topOfTurn + 1
    var end   = bottomIdx

    if rotation.trimEnabled then
      val tailThreshold = rotation.tailTrimThreshold.value
      val tailCap       = rotation.tailTrimCap.value
      val headThreshold = rotation.headTrimThreshold.value
      val headCap       = rotation.headTrimCap.value

      var cut      = 0.0
      var trimTail = true
      while trimTail do {
        val a = turnAngles(end)
        if end > topOfTurn + 1 && Math.abs(a) <= tailThreshold && Math.abs(cut - a) <= tailCap then
          cut -= a
          end -= 1
        else 
          trimTail = false
      }

      var trimHead = true
      while trimHead && start < end do {
        val a = turnAngles(start)
        if Math.abs(a) <= headThreshold && Math.abs(cut - a) <= headCap then
          cut -= a
          start += 1
        else 
          trimHead = false
      }

    var total = 0.0
    for i <- start to end do {
      val turnAngle = turnAngles(i)
      if turnAngle < rotation.fullTurnAngle.value then total += turnAngle
    }

    total
  }

  // First-max scan over the half-open window [from, until): the earliest point achieving the maximum of `f` among
  // strictly positive values. Values at or below zero never qualify.
  private def argmax(from: Int, until: Int)(f: Int => Double): Option[Int] = (from until until)
    .view
    .map(i => f(i) -> i)
    .filter(_._1 > 0)
    .maxByOption(_._1)
    .map(_._2)

  // Per-sample turn angles (heading deltas wrapped to [-180, 180]) with segment tracking. Inside a steep fast dive, a
  // run of samples whose turn direction is opposite to the established one opens a segment; while it lasts (the
  // opposite state persists, the dive persists, and the track stays within the displacement cap of the segment start),
  // the segment's angles are replaced by dt-weighted shares of the net heading change from the sample before the
  // segment to its end, resolved into the pre-segment direction and only when the resolved magnitude is under the
  // segment cap. The displacement test is dead-reckoned from the horizontal velocities (the stream carries no
  // positions).
  private def turnAnglesWithSegments(series: PointSeries, config: SwoopConfig): Vector[Double] = {
    val n       = series.length
    val turn    = Array.fill(n)(0.0)
    val heading = (0 until n).map(series.heading).toArray
    val timeMs  = (0 until n).map(series.timeMillis).toArray

    if n >= 2 then
      var prev     = 2 // 1 = right run, 0 = left run, 2 = straight
      var segStart = 0 // 0 = no open segment
      var segX     = 0.0
      var segY     = 0.0

      for i <- 1 until n do {
        val d = wrapDegrees(heading(i) - heading(i - 1))
        turn(i) = d

        val diveAngleCapExceeded = diveAngle(series, i) > config.rotation.diveAngleCap.value
        val diveSpeedCapExceeded = series.verticalSpeed(i) > config.rotation.diveVerticalSpeed.value

        val inDive = diveAngleCapExceeded && diveSpeedCapExceeded

        val state   = turnDirection(d, config)
        val flipped = (prev == 1 && state == 0) || (prev == 0 && state == 1)

        if segStart == 0 then
          if flipped && inDive then
            segStart = i
            segX = 0.0
            segY = 0.0
        else
          val dtSec = (timeMs(i) - timeMs(i - 1)) / 1000.0
          segX += series.northSpeed(i) * dtSec
          segY += series.eastSpeed(i) * dtSec

          val segmentEnded = !flipped || !inDive ||
            Math.hypot(segX, segY) > config.rotation.segmentDisplacementCap.value

          if segmentEnded then
            resolveSegment(turn, heading, timeMs, segStart, i - 1, prev, config)
            segStart = 0

        if segStart == 0 && state != 2 then prev = state
      }

    turn.toVector
  }

  // The dive angle at point `i`: the arctangent of the vertical speed over the horizontal speed, in degrees; +/-90 when
  // there is no horizontal speed.
  private def diveAngle(series: PointSeries, i: Int): Double = {
    val h        = series.horizontalSpeed(i)
    val vertical = series.verticalSpeed(i)
    if h == 0.0 then
      if vertical < 0.0 then -90.0 else 90.0
    else
      Math.atan(vertical / h) * 180.0 / Math.PI
  }

  // The turn direction of a per-sample angle: 1 = right run, 0 = left run, 2 = straight.
  private def turnDirection(d: Double, config: SwoopConfig): Int =
    if d > config.rotation.turnStateThreshold.value then 1
    else if d < -config.rotation.turnStateThreshold.value then 0
    else 2

  // Replaces the angles of a closed segment with dt-weighted shares of the net heading change from the sample before
  // the segment to its end, resolved into the pre-segment direction and only when the resolved magnitude is under the
  // segment cap.
  private def resolveSegment(
    turn: Array[Double],
    heading: Array[Double],
    timeMs: Array[Double],
    segStart: Int,
    end: Int,
    direction: Int,
    config: SwoopConfig,
  ): Unit = {
    var total = heading(end) - heading(segStart - 1)
    if total < 0.0 && direction == 1 then total += 360.0
    if total > 0.0 && direction == 0 then total -= 360.0

    if Math.abs(total) < config.rotation.segmentAngleCap.value then
      val rate = 1000.0 * total / (timeMs(end) - timeMs(segStart - 1))
      for k <- segStart to end do {
        turn(k) = (timeMs(k) - timeMs(k - 1)) * rate / 1000.0
      }
  }

  // Wraps a heading delta to [-180, 180].
  private def wrapDegrees(raw: Double): Double =
    if raw > 180.0 then
      raw - 360.0
    else if raw < -180.0 then
      raw + 360.0
    else
      raw

}
