package com.colofabrix.scala.beesight.swoop.computations

import com.colofabrix.scala.beesight.config.SwoopConfig
import com.colofabrix.scala.beesight.math.Heading
import com.colofabrix.scala.beesight.swoop.model.PointSeries
import java.time.Duration
import scala.annotation.tailrec

/**
 * The top of turn: from the dive point, walk back to the first point below the pull threshold (falling back to the
 * minimum-descent point if the threshold is never crossed), then back while the descent is still accelerating
 * downward, then through the steepening accumulator walk, and finally back while still accelerating downward. All
 * walks are bounded below by the pattern entry.
 */
private[swoop] object SwoopTopOfTurn {

  def find(series: PointSeries, diveIdx: Int, patternEntry: Int, config: SwoopConfig): Int = {
    val pullThreshold = config.speeds.pullThreshold.value

    def walkBack(i: Int)(pred: Int => Boolean): Int =
      @tailrec def loop(i: Int): Int =
        if patternEntry < i && pred(i) then 
          loop(i - 1) 
        else 
          i
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
      if series.verticalSpeed(decelerationEnd) >= config.speeds.steepeningEntry.value then
        steepeningWalk(series, decelerationEnd, patternEntry, config)
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
  private def steepeningWalk(series: PointSeries, startIdx: Int, patternEntry: Int, config: SwoopConfig): Int = {
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
        // FIXME: Give names to the two sides on the expression around ||
        val capsExceeded =
          turnRate > config.angles.maxTurnRate.value || altitudeGain > config.altitudes.maxAltitudeGain.value

        if capsExceeded then
          stop = true // cap fired: keep the starting point
        else if steepening > config.speeds.steepeningAccumulator.value then
          result = i - 1
          stop = true
        else
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
  private def isSteepening(series: PointSeries, i: Int): Boolean =
    val dt = Duration.between(series.time(i - 1), series.time(i)).toMillis / 1000.0

    val vz     = series.verticalSpeed(i)
    val vzPrev = series.verticalSpeed(i - 1)

    val timeAdvances = dt > 0
    val vzIsRising   = (vz - vzPrev) / dt > 0

    timeAdvances && vzIsRising

}
