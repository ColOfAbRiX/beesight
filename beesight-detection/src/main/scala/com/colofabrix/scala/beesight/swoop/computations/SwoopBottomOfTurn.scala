package com.colofabrix.scala.beesight.swoop.computations

import com.colofabrix.scala.beesight.config.SwoopConfig
import com.colofabrix.scala.beesight.swoop.model.PointSeries
import scala.annotation.tailrec

/**
 * The bottom of turn: walk forward from the rollout while the altitude strictly decreases or the descent stays at
 * least the bottom descent threshold - the bottom candidate; then take the altitude crossing (first point more than
 * the offset above the bottom altitude walking backward) or the descent-rate crossing walking forward - when both
 * exist, the one with the earlier interpolated crossing time wins - else the point after the rollout.
 */
private[swoop] object SwoopBottomOfTurn {

  def find(series: PointSeries, rolloutIdx: Int, stopIdx: Int, config: SwoopConfig): Int = {
    val bottomCandidate = bottomCandidateWalk(series, rolloutIdx, stopIdx, config)

    val backCross = backwardAltitudeBracket(series, rolloutIdx, bottomCandidate, config)
    val fwdCross  = forwardDescentBracket(series, rolloutIdx, stopIdx, config)

    earlierCrossing(backCross, fwdCross).getOrElse(rolloutIdx + 1)
  }

  // Forward walk from the rollout while the altitude strictly decreases or the descent stays fast; the last such point 
  // is the bottom candidate.
  private def bottomCandidateWalk(series: PointSeries, rolloutIdx: Int, stopIdx: Int, config: SwoopConfig): Int =
    @tailrec
    def walk(k: Int, minAltitude: Double, bottomCandidate: Int): Int =
      val j = k + 1

      if stopIdx < j then
        bottomCandidate
      else
        val stillDescending = series.altitude(j) < minAltitude
        val stillFast       = series.verticalSpeed(j) >= config.speeds.bottomDescentThreshold.value

        if stillDescending || stillFast then
          walk(j, series.altitude(j), bottomCandidate)
        else
          k

    walk(rolloutIdx, series.altitude(rolloutIdx), stopIdx)

  // Backward bracket: first point more than the altitude offset above the bottom altitude, with the interpolated 
  // crossing time at the target altitude.
  private def backwardAltitudeBracket(
    series: PointSeries,
    rolloutIdx: Int,
    bottomCandidate: Int,
    config: SwoopConfig
  ): Option[(Int, Double)] =
    val targetAltitude = series.altitude(bottomCandidate) + config.altitudes.bottomAltitudeOffset.value

    @tailrec
    def walk(k: Int): Option[(Int, Double)] =
      val j = k - 1
      if j <= rolloutIdx then
        None
      else if series.altitude(j) > targetAltitude then
        val altDelta  = series.altitude(j + 1) - series.altitude(j)
        val fraction  = (targetAltitude - series.altitude(j)) / altDelta
        val timeDelta = series.timeMillis(j + 1) - series.timeMillis(j)
        Some((j, series.timeMillis(j) + fraction * timeDelta))
      else
        walk(j)

    walk(bottomCandidate)

  // Forward bracket: first point whose descent rate drops to the crossing threshold, with the interpolated crossing time.
  private def forwardDescentBracket(
    series: PointSeries,
    rolloutIdx: Int,
    stopIdx: Int,
    config: SwoopConfig
  ): Option[(Int, Double)] =
    val vzCross = config.speeds.bottomVzCross.value

    @tailrec
    def walk(k: Int): Option[(Int, Double)] =
      val j = k + 1
      if stopIdx < j then
        None
      else if series.verticalSpeed(j) <= vzCross then
        val vzDelta   = series.verticalSpeed(j) - series.verticalSpeed(k)
        val fraction  = (vzCross - series.verticalSpeed(k)) / vzDelta
        val timeDelta = series.timeMillis(j) - series.timeMillis(k)
        Some((k, series.timeMillis(k) + fraction * timeDelta))
      else
        walk(j)

    walk(rolloutIdx)

  // The crossing with the earlier interpolated time, when both exist.
  private def earlierCrossing(back: Option[(Int, Double)], fwd: Option[(Int, Double)]): Option[Int] =
    (back, fwd) match {
      case (Some((b, bt)), Some((f, ft))) => if bt <= ft then Some(b) else Some(f)
      case (Some((b, _)), None)           => Some(b)
      case (None, Some((f, _)))           => Some(f)
      case (None, None)                   => None
    }

}
