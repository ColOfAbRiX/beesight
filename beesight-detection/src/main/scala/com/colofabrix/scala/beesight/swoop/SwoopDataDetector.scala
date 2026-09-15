package com.colofabrix.scala.beesight.swoop

import cats.effect.kernel.Concurrent
import cats.syntax.functor.*
import com.colofabrix.scala.beesight.config.{ DetectionConfig, SwoopConfig }
import com.colofabrix.scala.beesight.math.Heading
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.preprocess.*
import java.time.Duration

/**
 * Computes swoop metrics with gSwoop's detection pipeline (gswoop/NOTES.md §6 and the decompiled `detect_*`
 * functions):
 *
 *   1. Pattern entry: the first crossing below 2500 ft AGL above the detected ground.
 *   2. Dive point: the first point achieving the maximum descent rate below the 1000 ft AGL cap.
 *   3. Top of turn: the walk-back from the dive point - pull threshold (25 mph), deceleration boundary, steepening
 *      accumulator (15 mph of descent-rate increase, capped at 500 ft of altitude gain and 30 deg/s of turn rate).
 *   4. Stop: the end of motion after the dive - the first 10 mph descent / 20 mph horizontal slow point fixes the
 *      swoop heading, then the stop is the first point deviating more than 45 deg from it or slowing below 2 mph.
 *   5. Rollout: the maximum-descent point within the 100 ft window above the stop altitude.
 *   6. Bottom of turn: the first point more than 1 m above the bottom altitude after the rollout, falling back to
 *      the 1.25 m/s descent crossing, then to the point after the rollout.
 *   7. Metrics: maximum speeds over gSwoop's windows ([top of turn, stop) for vertical/total, [rollout, stop) for
 *      horizontal) and the signed per-sample turn-angle sum over (top of turn, bottom of turn].
 *
 * Every stage mirrors gSwoop's error paths: when a stage cannot find its event, no swoop is reported at all. All
 * reported altitudes are AGL relative to the detected ground (the mean altitude over the last 3 s minus 2 m).
 */
final class SwoopDataDetector private (config: SwoopConfig) {

  //  gSwoop Constants  //

  private val MpsToMph     = 2.2369362920544
  private val MetresToFeet = 3.28083989501312

  // FIXME: All internal quantities, including constants, must be expressed in SIU units (meters, seconds, ...). mph values can be added as comments
  private val patternEntryAltitude    = 2500.0 / MetresToFeet // 2500 ft (m AGL above the detected ground)
  private val diveAltitudeCap         = 1000.0 / MetresToFeet // 1000 ft (m AGL above the detected ground)
  private val steepeningEntry         = 15.0 / MpsToMph       // 15 mph (m/s)
  private val steepeningAccumulator   = 15.0                  // 15 mph of accumulated descent-rate increase (mph)
  private val maxAltitudeGain         = 500.0 / MetresToFeet  // 500 ft (m)
  private val maxTurnRate             = 30.0                  // deg/s
  private val stopDescentThreshold    = 10.0 / MpsToMph       // 10 mph (m/s)
  private val stopHorizontalThreshold = 20.0 / MpsToMph       // 20 mph (m/s)
  private val stopEndHorizontal       = 2.0 / MpsToMph        // 2 mph (m/s)
  private val stopHeadingDeviation    = 45.0                  // deg
  private val bottomDescentThreshold  = 10.0 / MpsToMph       // 10 mph (m/s)
  private val bottomVzCross           = 1.25                  // m/s
  private val bottomAltitudeOffset    = 1.0                   // m

  //  Public API  //

  /**
   * Detects swoop metrics from the flight data stream.
   *
   * A swoop is reported only when the recording is classified as a jump AND the detection cascade finds swoop data:
   * the events are a secondary gate - a canopy point, since a swoop is a canopy maneuver and cannot exist without
   * one.
   */
  def detect[F[_]: Concurrent, A: PointReader](events: FlightEvents, stream: fs2.Stream[F, A]): F[Option[SwoopData]] =
    detectWithTrace(events, stream).map(_.result)

  //  TEMPORARY trace hook for the gSwoop port validation - remove after the experiments  //

  /** Runs the same pipeline as [[detect]] but returns the full detection cascade for inspection. */
  private[swoop] def detectWithTrace[F[_]: Concurrent, A: PointReader](events: FlightEvents, stream: fs2.Stream[F, A]): F[SwoopTrace] =
    stream
      .zipWithIndex
      .map(IndexedPoint.fromZip)
      .compile
      .toVector
      .map(computeSwoopMetricsWithTrace(_, events))

  //  Swoop Detection  //

  private def computeSwoopMetrics(dataPoints: Vector[IndexedPoint]): Option[SwoopData] =
    computeSwoopMetricsWithTrace(dataPoints, FlightEvents.empty).result

  private def computeSwoopMetricsWithTrace(dataPoints: Vector[IndexedPoint], events: FlightEvents): SwoopTrace =
    if dataPoints.length < 2 then
      SwoopTrace(dataPoints, Double.NaN, None, None, None, None, None, None, None)
    else
      val ground = detectedGround(dataPoints, events.landing)

      val patternEntry = findPatternEntry(dataPoints, ground)
      val dive         = patternEntry.flatMap(pe => findDivePoint(dataPoints, pe, ground))
      val topOfTurn    = dive.map(d => findTopOfTurn(dataPoints, d, patternEntry.getOrElse(0)))
      val stop         = dive.flatMap(d => findStop(dataPoints, d))
      val rollout      = topOfTurn.flatMap(t => stop.flatMap(s => findRolloutStart(dataPoints, t, s)))
      val bottom       = rollout.flatMap(r => stop.map(s => findBottomOfTurn(dataPoints, r, s)))
      // Secondary gate: the recording must be classified as a jump with a canopy flight - a swoop is a canopy
      // maneuver, so a recording without a canopy event cannot contain one.
      val hasJumpEvents = events.canopy.isDefined

      val result       = dive
        .flatMap { d =>
          topOfTurn.flatMap { t =>
            rollout.flatMap { r =>
              stop.map(s => buildSwoopData(dataPoints, d, t, r, bottom.getOrElse(r), s, ground))
            }
          }
        }
        .filter(_ => hasJumpEvents)

      SwoopTrace(dataPoints, ground, patternEntry, dive, topOfTurn, stop, rollout, bottom, result)

  /**
   * Landing-ground estimate. Primary: the mean altitude over the ~5 s of points from the detected landing point
   * onwards, minus 2 m. Fallback (no landing event): gSwoop's emulation (decompiled_all.c:8671).
   */
  private def detectedGround(dataPoints: Vector[IndexedPoint], landing: Option[EventPoint]): Double =
    landing
      .filter(l => 0 <= l.index && l.index < dataPoints.length)
      .map { l =>
        val startTime = dataPoints(l.index.toInt).point.time
        val window    = dataPoints.view.drop(l.index.toInt).takeWhile(!_.point.time.isAfter(startTime.plusSeconds(10)))
        window.map(_.point.altitude).sum / window.size - 2.0
      }
      .getOrElse(gswoopGround(dataPoints))

  /**
   * gSwoop's landing-ground emulation (decompiled_all.c:8671): walk back to the last slow point (total speed below
   * 10 mph, after landing), then average the altitude over the ~3 s forward window starting there, minus 2 m. When
   * no slow point exists, the ground falls back to the last point's altitude minus 2 m.
   */
  private def gswoopGround(dataPoints: Vector[IndexedPoint]): Double =
    val speedThreshold = 10.0 / MpsToMph // 10 mph (m/s)

    (dataPoints.length - 1 to 0 by -1).find(i => dataPoints(i).point.speed.magnitude < speedThreshold) match
      case Some(slowIdx) =>
        val startTime = dataPoints(slowIdx).point.time
        val window    = dataPoints.view.drop(slowIdx).takeWhile(!_.point.time.isAfter(startTime.plusSeconds(10)))
        window.map(_.point.altitude).sum / window.size - 2.0
      case None =>
        dataPoints.last.point.altitude - 2.0

  /** The first crossing below 2500 ft AGL above the detected ground (NOTES §6.2). */
  private def findPatternEntry(dataPoints: Vector[IndexedPoint], ground: Double): Option[Int] =
    val agl = (i: Int) => dataPoints(i).point.altitude - ground

    (0 until dataPoints.length - 1)
      .find(i => agl(i) >= patternEntryAltitude && agl(i + 1) < patternEntryAltitude)
      .map(_ + 1)

  /**
   * The dive point: the first point achieving the maximum descent rate below the 1000 ft course-frame cap, mirroring
   * gSwoop's global argmax scan (NOTES §6.3 step 1). Points at or below zero descent never qualify, so profiles
   * without a dive yield no swoop.
   */
  private def findDivePoint(dataPoints: Vector[IndexedPoint], patternEntry: Int, ground: Double): Option[Int] =
    val agl = (i: Int) => dataPoints(i).point.altitude - ground

    var bestIdx: Option[Int] = None
    var bestDescent          = 0.0

    for i <- patternEntry until dataPoints.length do
      val descent = dataPoints(i).point.speed.vertical
      if agl(i) < diveAltitudeCap && bestDescent < descent then
        bestIdx = Some(i)
        bestDescent = descent

    bestIdx

  /**
   * gSwoop's `detect_top_of_turn` walk-back (NOTES §6.3): from the dive point, walk back to the first point below the
   * pull threshold (falling back to the minimum-descent point if the threshold is never crossed), then back while the
   * descent is still accelerating downward, then through the steepening accumulator walk, and finally back while still
   * accelerating downward. All walks are bounded below by the pattern entry.
   */
  private def findTopOfTurn(dataPoints: Vector[IndexedPoint], diveIdx: Int, patternEntry: Int): Int =
    val vz = (j: Int) => dataPoints(j).point.speed.vertical

    def walkBack(i: Int)(pred: Int => Boolean): Int =
      if patternEntry < i && pred(i) then walkBack(i - 1)(pred) else i

    val crossed =
      walkBack(diveIdx)(i => vz(i) >= config.pullThreshold)

    val afterPull =
      if vz(crossed) >= config.pullThreshold then
        // gSwoop keeps the LARGEST index among the minima (last occurrence of the minimum descent)
        (patternEntry to diveIdx).reverse.minBy(vz)
      else
        crossed

    val decelerationEnd =
      walkBack(afterPull)(i => isSteepening(dataPoints, i))

    val steepeningEnd =
      if vz(decelerationEnd) >= steepeningEntry then
        steepeningWalk(dataPoints, decelerationEnd, patternEntry)
      else
        decelerationEnd

    walkBack(steepeningEnd)(i => isSteepening(dataPoints, i))

  /**
   * gSwoop's steepening accumulator walk: walks backward from the deceleration boundary accumulating the descent-rate
   * increase over the samples that are still accelerating downward (resetting on the others), and stops when the
   * accumulation exceeds 15 mph - the dive steepening began at the sample before. The walk is aborted, keeping the
   * starting point, when the altitude gain exceeds 500 ft or the turn rate exceeds 30 deg/s.
   */
  private def steepeningWalk(dataPoints: Vector[IndexedPoint], startIdx: Int, patternEntry: Int): Int =
    val vz = (j: Int) => dataPoints(j).point.speed.vertical

    var result       = startIdx
    var altitudeGain = 0.0 // metres gained walking backward (gSwoop's altLossFt)
    var steepening   = 0.0 // accumulated descent-rate increase (mph)
    var i            = startIdx
    var stop         = false

    while !stop && patternEntry < i do
      val dtMs = Duration.between(dataPoints(i - 1).point.time, dataPoints(i).point.time).toMillis

      if dtMs <= 0 then
        stop = true
      else
        val headingDelta =
          Heading.angularDistance(
            Heading.headingDegrees(dataPoints(i - 1).point.speed),
            Heading.headingDegrees(dataPoints(i).point.speed)
          )

        altitudeGain += dataPoints(i - 1).point.altitude - dataPoints(i).point.altitude

        if (vz(i) - vz(i - 1)) / (dtMs / 1000.0) <= 0 then
          steepening = 0.0 // not accelerating downward: reset the accumulator
        else
          steepening += (vz(i) - vz(i - 1)) * MpsToMph

        if headingDelta / (dtMs / 1000.0) > maxTurnRate || altitudeGain > maxAltitudeGain then
          stop = true // cap fired: keep the starting point, like gSwoop
        else if steepening > steepeningAccumulator then
          result = i - 1
          stop = true
        else
          i = i - 1

    result

  /**
   * True when the vertical speed is still increasing at point `i` (the descent is accelerating downward there, gSwoop's
   * `az < 0`).
   */
  private def isSteepening(dataPoints: Vector[IndexedPoint], i: Int): Boolean =
    val dt = Duration.between(dataPoints(i - 1).point.time, dataPoints(i).point.time).toMillis / 1000.0

    val vz     = dataPoints(i).point.speed.vertical
    val vzPrev = dataPoints(i - 1).point.speed.vertical

    val timeAdvances = dt > 0
    val vzIsRising   = (vz - vzPrev) / dt > 0

    timeAdvances && vzIsRising

  /**
   * gSwoop's `detect_stop` (NOTES §6.4): from the dive point forward, the first point below 10 mph of descent and
   * 20 mph of horizontal speed fixes the swoop heading; from there forward, the first point below 2 mph of horizontal
   * speed or deviating more than 45 deg from the swoop heading is the stop.
   */
  private def findStop(dataPoints: Vector[IndexedPoint], diveIdx: Int): Option[Int] =
    val vz   = (j: Int) => dataPoints(j).point.speed.vertical
    val horz = (j: Int) => dataPoints(j).point.speed.horizontal
    val hdg  = (j: Int) => Heading.headingDegrees(dataPoints(j).point.speed)

    val headingIdx =
      (diveIdx until dataPoints.length)
        .find(i => vz(i) < stopDescentThreshold && horz(i) < stopHorizontalThreshold)

    headingIdx.flatMap { slowIdx =>
      val swoopHeading = hdg(slowIdx)

      (slowIdx until dataPoints.length)
        .find { i =>
          horz(i) < stopEndHorizontal ||
          Heading.angularDistance(hdg(i), swoopHeading) > stopHeadingDeviation
        }
    }

  /**
   * gSwoop's `detect_rollout_start` (NOTES §6.5): from the top of turn forward, the first point less than 100 ft above
   * the stop altitude starts the walk; walking backward while the descent rate strictly increases yields the
   * maximum-descent-rate point - the rollout start. The walk never moving keeps the starting point; reaching the top
   * of turn fails the detection.
   */
  private def findRolloutStart(dataPoints: Vector[IndexedPoint], topOfTurn: Int, stopIdx: Int): Option[Int] =
    val vz  = (j: Int) => dataPoints(j).point.speed.vertical
    val alt = (j: Int) => dataPoints(j).point.altitude

    var startIdx = topOfTurn
    while startIdx <= stopIdx && (alt(startIdx) - alt(stopIdx)) >= config.rolloutWindow do
      startIdx += 1

    var curIdx   = startIdx
    var negVzMax = vz(startIdx)
    var rollout  = startIdx
    var failed   = false
    var walking  = true

    while walking do
      val prev = curIdx - 1
      if prev < topOfTurn then
        failed = true // gSwoop: rolloutIdx stays 0 - "could not find rollout"
        walking = false
      else
        val ascending = negVzMax < vz(prev)
        rollout = curIdx
        negVzMax = vz(prev)
        curIdx = prev
        walking = ascending

    Option.unless(failed)(rollout)

  /**
   * gSwoop's `find_bottom_bracket` (NOTES §6.6): walk forward from the rollout while the altitude strictly decreases
   * or the descent stays at least 10 mph - the bottom candidate; then take the altitude crossing (first point more
   * than 1 m above the bottom altitude walking backward) or the 1.25 m/s descent crossing walking forward - when both
   * exist, the one with the earlier interpolated crossing time wins (decompiled_all.c:8866-8901) - else the point
   * after the rollout.
   */
  private def findBottomOfTurn(dataPoints: Vector[IndexedPoint], rolloutIdx: Int, stopIdx: Int): Int =
    val vz   = (j: Int) => dataPoints(j).point.speed.vertical
    val alt  = (j: Int) => dataPoints(j).point.altitude
    val time = (j: Int) => dataPoints(j).point.time.toEpochMilli.toDouble

    //  Bottom candidate: forward walk while descending or still fast  //
    var minAltitude    = alt(rolloutIdx)
    var k              = rolloutIdx
    var bottomCandidate = stopIdx
    var walking        = true

    while walking do
      val j = k + 1
      if stopIdx < j then
        walking = false
      else
        val stillDescending = alt(j) < minAltitude
        minAltitude = alt(j)
        val previous = k
        k = j
        if !(stillDescending || vz(j) >= bottomDescentThreshold) then
          bottomCandidate = previous
          walking = false

    //  Backward bracket: first point more than 1 m above the bottom altitude, with the interpolated
    //  crossing time at the target altitude  //
    val targetAltitude = alt(bottomCandidate) + bottomAltitudeOffset

    var backCross: Option[(Int, Double)] = None
    k = bottomCandidate
    walking = true

    while walking do
      val j = k - 1
      if j <= rolloutIdx then
        walking = false
      else
        k = j
        if alt(j) > targetAltitude then
          val fraction = (targetAltitude - alt(j)) / (alt(j + 1) - alt(j))
          backCross = Some((j, time(j) + fraction * (time(j + 1) - time(j))))
          walking = false

    //  Forward walk while the descent rate stays above 1.25 m/s, with the interpolated crossing time  //
    var fwdCross: Option[(Int, Double)] = None
    k = rolloutIdx
    walking = true

    while walking do
      val j = k + 1
      if stopIdx < j then
        walking = false
      else
        val previous = k
        k = j
        if vz(j) <= bottomVzCross then
          val fraction = (bottomVzCross - vz(previous)) / (vz(j) - vz(previous))
          fwdCross = Some((previous, time(previous) + fraction * (time(j) - time(previous))))
          walking = false

    //  gSwoop picks the crossing with the earlier interpolated time (decompiled_all.c:8866-8901)  //
    (backCross, fwdCross) match
      case (Some((b, bt)), Some((f, ft))) => if bt <= ft then b else f
      case (Some((b, _)), None)           => b
      case (None, Some((f, _)))           => f
      case (None, None)                   => rolloutIdx + 1

  //  Metrics Computation  //

  /** Assembles all swoop metrics; event altitudes are reported AGL (relative to the detected ground). */
  private def buildSwoopData(
    dataPoints: Vector[IndexedPoint],
    diveIdx: Int,
    topOfTurn: Int,
    rolloutIdx: Int,
    bottomIdx: Int,
    stopIdx: Int,
    ground: Double
  ): SwoopData =
    val agl  = (j: Int) => dataPoints(j).point.altitude - ground
    val last = dataPoints.length - 1

    // First-max scans over gSwoop's windows: vertical/total over [top of turn, stop), horizontal over [rollout, stop).
    var maxVertIdx  = -1
    var maxVert     = 0.0
    var maxTotIdx   = -1
    var maxTot      = 0.0
    var maxHorzIdx  = -1
    var maxHorz     = 0.0

    for i <- topOfTurn until stopIdx do
      val descent = dataPoints(i).point.speed.vertical
      val total   = dataPoints(i).point.speed.magnitude
      if maxVert < descent then
        maxVertIdx = i
        maxVert = descent
      if maxTot < total then
        maxTotIdx = i
        maxTot = total

    for i <- rolloutIdx until stopIdx do
      val horizontal = dataPoints(i).point.speed.horizontal
      if maxHorz < horizontal then
        maxHorzIdx = i
        maxHorz = horizontal

    // Degenerate windows fall back to the dive point, whose descent is strictly positive by construction.
    if maxVertIdx < 0 then
      maxVertIdx = diveIdx
      maxVert = dataPoints(diveIdx).point.speed.vertical
    if maxTotIdx < 0 then
      maxTotIdx = diveIdx
      maxTot = dataPoints(diveIdx).point.speed.magnitude
    if maxHorzIdx < 0 then
      maxHorzIdx = rolloutIdx
      maxHorz = dataPoints(rolloutIdx).point.speed.horizontal

    val degreesOfRotation = turnDegrees(dataPoints, topOfTurn, Math.min(bottomIdx, last))

    SwoopData(
      turnInitiation = EventPoint(dataPoints(topOfTurn).idx, agl(topOfTurn)),
      rolloutStart = EventPoint(dataPoints(rolloutIdx).idx, agl(rolloutIdx)),
      maxVerticalSpeed = SpeedAtAltitude(maxVert, agl(maxVertIdx)),
      maxTotalSpeed = SpeedAtAltitude(maxTot, agl(maxTotIdx)),
      maxHorizontalSpeed = SpeedAtAltitude(maxHorz, agl(maxHorzIdx)),
      degreesOfRotation = degreesOfRotation,
      timeToExecuteTurn = Duration.between(dataPoints(topOfTurn).point.time, dataPoints(rolloutIdx).point.time),
    )

  /**
   * gSwoop's `compute_turn_degrees` (NOTES §6.10): the signed sum of the per-sample turn angles over
   * (top of turn, bottom of turn], skipping values of 360 deg or more. The angles come from the unwrap pass.
   */
  private def turnDegrees(dataPoints: Vector[IndexedPoint], topOfTurn: Int, bottomIdx: Int): Double =
    val turnAngles = turnAnglesWithUnwrap(dataPoints)

    var total = 0.0
    for i <- topOfTurn + 1 to bottomIdx do
      val turnAngle = turnAngles(i)
      if turnAngle < 360.0 then total += turnAngle

    total

  /**
   * gSwoop's `compute_turn_angle_unwrap` (decompiled_all.c:7447-7546): per-sample turn angles (heading deltas wrapped
   * to [-180, 180]) with segment tracking. Inside a steep fast dive (dive angle > 60 deg AND descent > 60 mph), a run
   * of samples whose turn direction is opposite to the established one opens a segment; while it lasts (the opposite
   * state persists, the dive persists, and the track stays within 30 ft of the segment start), the segment's angles
   * are replaced by dt-weighted shares of the net heading change from the sample before the segment to its end,
   * resolved into the pre-segment direction and only when the resolved magnitude is under 315 deg. The displacement
   * test is dead-reckoned from the horizontal velocities (the port stream carries no positions).
   */
  private def turnAnglesWithUnwrap(dataPoints: Vector[IndexedPoint]): Vector[Double] =
    val n       = dataPoints.length
    val turn    = Array.fill(n)(0.0)
    val heading = dataPoints.map(p => Heading.headingDegrees(p.point.speed))
    val timeMs  = dataPoints.map(_.point.time.toEpochMilli.toDouble)

    /** gSwoop's dive angle: atan(-vz_up / horz), positive when descending; +/-90 when there is no horizontal speed. */
    def diveAngle(i: Int): Double =
      val h        = dataPoints(i).point.speed.horizontal
      val vertical = dataPoints(i).point.speed.vertical
      if h == 0.0 then
        if vertical < 0.0 then -90.0 else 90.0
      else
        Math.atan(vertical / h) * 180.0 / Math.PI

    if n >= 2 then
      var prev     = 2 // 1 = right run, 0 = left run, 2 = straight
      var segStart = 0 // 0 = no open segment
      var segX     = 0.0
      var segY     = 0.0

      for i <- 1 until n do
        val raw = heading(i) - heading(i - 1)
        val d   = if raw > 180.0 then raw - 360.0 else if raw < -180.0 then raw + 360.0 else raw
        turn(i) = d

        val inDive  = diveAngle(i) > 60.0 && dataPoints(i).point.speed.vertical * MpsToMph > 60.0
        val state   = if d > 2.0 then 1 else if d < -2.0 then 0 else 2
        val flipped = (prev == 1 && state == 0) || (prev == 0 && state == 1)

        if segStart == 0 then
          if flipped && inDive then
            segStart = i
            segX = 0.0
            segY = 0.0
        else
          val dtSec = (timeMs(i) - timeMs(i - 1)) / 1000.0
          segX += dataPoints(i).point.speed.north * dtSec
          segY += dataPoints(i).point.speed.east * dtSec

          if !flipped || !inDive || Math.hypot(segX, segY) * MetresToFeet > 30.0 then
            val end   = i - 1
            var total = heading(end) - heading(segStart - 1)
            if total < 0.0 && prev == 1 then total += 360.0
            if total > 0.0 && prev == 0 then total -= 360.0
            if Math.abs(total) < 315.0 then
              val rate = 1000.0 * total / (timeMs(end) - timeMs(segStart - 1))
              var k    = segStart
              while k <= end do
                turn(k) = (timeMs(k) - timeMs(k - 1)) * rate / 1000.0
                k += 1
            segStart = 0

        if segStart == 0 && state != 2 then prev = state

    turn.toVector

}

object SwoopDataDetector {

  def apply(config: DetectionConfig): SwoopDataDetector =
    new SwoopDataDetector(config.swoop)

}

/** TEMPORARY trace of the gSwoop detection cascade (port validation) - remove after the experiments. */
final private[swoop] case class SwoopTrace(
  points: Vector[IndexedPoint],
  ground: Double,
  patternEntry: Option[Int],
  dive: Option[Int],
  topOfTurn: Option[Int],
  stop: Option[Int],
  rollout: Option[Int],
  bottom: Option[Int],
  result: Option[SwoopData],
)
