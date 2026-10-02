package com.colofabrix.scala.beesight.swoop.computations

import com.colofabrix.scala.beesight.math.Heading
import com.colofabrix.scala.beesight.swoop.SwoopConstants
import com.colofabrix.scala.beesight.swoop.model.PointSeries
import java.time.Duration
import scala.annotation.tailrec

/**
 * Detection of the swoop reference points over a flight recording: pattern entry, dive point, top of turn, stop,
 * rollout start, and bottom of turn. Each stage scans the point series for the sample satisfying its condition; when
 * a stage cannot find its event, the detection fails and no swoop is reported.
 */
private[swoop] object SwoopCascade {

  // FIXME: Make this file more FP by removing all the vars and keeping performance in mind
  // FIXME: Evaluate and discuss with the user if this file should be brokwn down in smaller files

  //  Pattern Entry  //

  /** The first crossing below the pattern-entry altitude above the detected ground. */
  def findPatternEntry(series: PointSeries, ground: Double): Option[Int] = {
    val agl = (i: Int) => series.altitude(i) - ground

    (0 until series.length - 1)
      .find { i =>
        val aboveOrAt = agl(i) >= SwoopConstants.patternEntryAltitude
        val below     = agl(i + 1) < SwoopConstants.patternEntryAltitude
        aboveOrAt && below
      }
      .map(_ + 1)
  }

  //  Dive Point  //

  /**
   * The dive point: the first point achieving the maximum descent rate below the altitude cap. Points at or below
   * zero descent never qualify, so profiles without a dive yield no swoop.
   */
  def findDivePoint(series: PointSeries, patternEntry: Int, ground: Double): Option[Int] = {
    val agl = (i: Int) => series.altitude(i) - ground

    var bestIdx: Option[Int] = None
    var bestDescent          = 0.0

    for i <- patternEntry until series.length do {
      val descent = series.verticalSpeed(i)
      if agl(i) < SwoopConstants.diveAltitudeCap && bestDescent < descent then {
        bestIdx = Some(i)
        bestDescent = descent
      }
    }

    bestIdx
  }

  //  Top of Turn  //

  /**
   * The top of turn: from the dive point, walk back to the first point below the pull threshold (falling back to the
   * minimum-descent point if the threshold is never crossed), then back while the descent is still accelerating
   * downward, then through the steepening accumulator walk, and finally back while still accelerating downward. All
   * walks are bounded below by the pattern entry.
   */
  def findTopOfTurn(series: PointSeries, diveIdx: Int, patternEntry: Int, pullThreshold: Double): Int = {
    def walkBack(i: Int)(pred: Int => Boolean): Int =
      @tailrec def loop(i: Int): Int = if patternEntry < i && pred(i) then loop(i - 1) else i
      loop(i)

    val crossed = walkBack(diveIdx)(i => series.verticalSpeed(i) >= pullThreshold)

    val afterPull =
      if series.verticalSpeed(crossed) >= pullThreshold then
        // keep the largest index among the minima (last occurrence of the minimum descent)
        (patternEntry to diveIdx)
          .reverse
          .minBy(series.verticalSpeed)
      else
        crossed

    val decelerationEnd = walkBack(afterPull)(i => isSteepening(series, i))

    val steepeningEnd =
      if series.verticalSpeed(decelerationEnd) >= SwoopConstants.steepeningEntry then
        steepeningWalk(series, decelerationEnd, patternEntry)
      else
        decelerationEnd

    walkBack(steepeningEnd)(i => isSteepening(series, i))
  }

  /**
   * Steepening accumulator walk: walks backward from the deceleration boundary accumulating the descent-rate increase
   * over the samples that are still accelerating downward (resetting on the others), and stops when the accumulation
   * exceeds the steepening threshold - the dive steepening began at the sample before. The walk is aborted, keeping
   * the starting point, when the altitude gain or the turn rate exceeds its cap.
   */
  private def steepeningWalk(series: PointSeries, startIdx: Int, patternEntry: Int): Int = {
    var result       = startIdx
    var altitudeGain = 0.0 // metres gained walking backward
    var steepening   = 0.0 // accumulated descent-rate increase (m/s)
    var i            = startIdx
    var stop         = false

    while !stop && patternEntry < i do {
      val dtMs = Duration.between(series.time(i - 1), series.time(i)).toMillis

      if dtMs <= 0 then
        stop = true
      else {
        val dtSec = dtMs / 1000.0

        altitudeGain += series.altitude(i - 1) - series.altitude(i)
        steepening = updatedSteepening(series, i, dtSec, steepening)

        val turnRate     = Heading.angularDistance(series.heading(i - 1), series.heading(i)) / dtSec
        val capsExceeded = turnRate > SwoopConstants.maxTurnRate || altitudeGain > SwoopConstants.maxAltitudeGain

        if capsExceeded then
          stop = true // cap fired: keep the starting point
        else if steepening > SwoopConstants.steepeningAccumulator then {
          result = i - 1
          stop = true
        } else
          i = i - 1
      }
    }

    result
  }

  /** The accumulator value after processing sample `i`: reset when the descent stops accelerating downward, increased by the descent-rate rise otherwise. */
  private def updatedSteepening(series: PointSeries, i: Int, dtSec: Double, steepening: Double): Double =
    val descentIncrease = series.verticalSpeed(i) - series.verticalSpeed(i - 1)

    if descentIncrease / dtSec <= 0 then
      0.0
    else
      steepening + descentIncrease

  /** True when the vertical speed is still increasing at point `i` (the descent is accelerating downward there). */
  private def isSteepening(series: PointSeries, i: Int): Boolean = {
    val dt = Duration.between(series.time(i - 1), series.time(i)).toMillis / 1000.0

    val vz     = series.verticalSpeed(i)
    val vzPrev = series.verticalSpeed(i - 1)

    val timeAdvances = dt > 0
    val vzIsRising   = (vz - vzPrev) / dt > 0

    timeAdvances && vzIsRising
  }

  //  Stop  //

  /**
   * The stop: from the dive point forward, the first point below the slow descent and slow horizontal thresholds
   * fixes the swoop heading; from there forward, the first point below the end horizontal speed or deviating more
   * than the heading deviation from the swoop heading is the stop.
   */
  def findStop(series: PointSeries, diveIdx: Int): Option[Int] = {
    val headingIdx = (diveIdx until series.length)
      .find { i =>
        val slowDescent    = series.verticalSpeed(i) < SwoopConstants.stopDescentThreshold
        val slowHorizontal = series.horizontalSpeed(i) < SwoopConstants.stopHorizontalThreshold
        slowDescent && slowHorizontal
      }

    headingIdx.flatMap { slowIdx =>
      lazy val swoopHeading = series.heading(slowIdx)

      (slowIdx until series.length)
        .find { i =>
          val nearlyStopped =
            series.horizontalSpeed(i) < SwoopConstants.stopEndHorizontal

          val turnedAway =
            Heading.angularDistance(series.heading(i), swoopHeading) > SwoopConstants.stopHeadingDeviation

          nearlyStopped || turnedAway
        }
    }
  }

  //  Rollout Start  //

  /**
   * The rollout start: from the top of turn forward, the first point less than the rollout window above the stop
   * altitude starts the walk; walking backward while the descent rate strictly increases yields the
   * maximum-descent-rate point - the rollout start. The walk never moving keeps the starting point; reaching the top
   * of turn fails the detection.
   */
  def findRolloutStart(series: PointSeries, topOfTurn: Int, stopIdx: Int, rolloutWindow: Double): Option[Int] = {
    var startIdx = topOfTurn

    while startIdx <= stopIdx && (series.altitude(startIdx) - series.altitude(stopIdx)) >= rolloutWindow do
      startIdx += 1

    var curIdx   = startIdx
    var negVzMax = series.verticalSpeed(startIdx)
    var rollout  = startIdx
    var failed   = false
    var walking  = true

    while walking do {
      val prev = curIdx - 1

      if prev < topOfTurn then
        failed = true // could not find the rollout
        walking = false
      else
        val ascending = negVzMax < series.verticalSpeed(prev)
        rollout = curIdx
        negVzMax = series.verticalSpeed(prev)
        curIdx = prev
        walking = ascending
    }

    Option.unless(failed)(rollout)
  }

  //  Bottom of Turn  //

  /**
   * The bottom of turn: walk forward from the rollout while the altitude strictly decreases or the descent stays at
   * least the bottom descent threshold - the bottom candidate; then take the altitude crossing (first point more than
   * the offset above the bottom altitude walking backward) or the descent-rate crossing walking forward - when both
   * exist, the one with the earlier interpolated crossing time wins - else the point after the rollout.
   */
  def findBottomOfTurn(series: PointSeries, rolloutIdx: Int, stopIdx: Int): Int = {
    val bottomCandidate = bottomCandidateWalk(series, rolloutIdx, stopIdx)

    val backCross = backwardAltitudeBracket(series, rolloutIdx, bottomCandidate)
    val fwdCross  = forwardDescentBracket(series, rolloutIdx, stopIdx)

    earlierCrossing(backCross, fwdCross).getOrElse(rolloutIdx + 1)
  }

  /** Forward walk from the rollout while the altitude strictly decreases or the descent stays fast; the last such point is the bottom candidate. */
  private def bottomCandidateWalk(series: PointSeries, rolloutIdx: Int, stopIdx: Int): Int =
    var minAltitude     = series.altitude(rolloutIdx)
    var k               = rolloutIdx
    var bottomCandidate = stopIdx
    var walking         = true

    while walking do {
      val j = k + 1

      if stopIdx < j then
        walking = false
      else
        val stillDescending = series.altitude(j) < minAltitude
        minAltitude = series.altitude(j)

        val previous = k
        k = j

        if !(stillDescending || series.verticalSpeed(j) >= SwoopConstants.bottomDescentThreshold) then
          bottomCandidate = previous
          walking = false
    }

    bottomCandidate

  /** Backward bracket: first point more than the altitude offset above the bottom altitude, with the interpolated crossing time at the target altitude. */
  private def backwardAltitudeBracket(
    series: PointSeries,
    rolloutIdx: Int,
    bottomCandidate: Int
  ): Option[(Int, Double)] =
    val targetAltitude = series.altitude(bottomCandidate) + SwoopConstants.bottomAltitudeOffset

    var cross   = Option.empty[(Int, Double)]
    var k       = bottomCandidate
    var walking = true

    while walking do {
      val j = k - 1
      if j <= rolloutIdx then
        walking = false
      else {
        k = j
        if series.altitude(j) > targetAltitude then
          val altDelta  = series.altitude(j + 1) - series.altitude(j)
          val fraction  = (targetAltitude - series.altitude(j)) / altDelta
          val timeDelta = series.timeMillis(j + 1) - series.timeMillis(j)
          val crossing  = series.timeMillis(j) + fraction * timeDelta
          cross = Some((j, crossing))
          walking = false
      }
    }

    cross

  /** Forward bracket: first point whose descent rate drops to the crossing threshold, with the interpolated crossing time. */
  private def forwardDescentBracket(series: PointSeries, rolloutIdx: Int, stopIdx: Int): Option[(Int, Double)] =
    var cross   = Option.empty[(Int, Double)]
    var k       = rolloutIdx
    var walking = true

    while walking do {
      val j = k + 1
      if stopIdx < j then
        walking = false
      else
        val previous = k
        k = j
        if series.verticalSpeed(j) <= SwoopConstants.bottomVzCross then {
          val vzDelta   = series.verticalSpeed(j) - series.verticalSpeed(previous)
          val fraction  = (SwoopConstants.bottomVzCross - series.verticalSpeed(previous)) / vzDelta
          val timeDelta = series.timeMillis(j) - series.timeMillis(previous)
          val crossing  = series.timeMillis(previous) + fraction * timeDelta
          cross = Some((previous, crossing))
          walking = false
        }
    }

    cross

  /** The crossing with the earlier interpolated time, when both exist. */
  private def earlierCrossing(back: Option[(Int, Double)], fwd: Option[(Int, Double)]): Option[Int] =
    (back, fwd) match
      case (Some((b, bt)), Some((f, ft))) => if bt <= ft then Some(b) else Some(f)
      case (Some((b, _)), None)           => Some(b)
      case (None, Some((f, _)))           => Some(f)
      case (None, None)                   => None

}
