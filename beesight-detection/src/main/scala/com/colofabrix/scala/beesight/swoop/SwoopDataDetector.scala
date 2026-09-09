package com.colofabrix.scala.beesight.swoop

import cats.effect.kernel.Concurrent
import cats.syntax.all.*
import com.colofabrix.scala.beesight.config.{ DetectionConfig, SwoopConfig }
import com.colofabrix.scala.beesight.math.Heading
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.preprocess.*
import java.time.Duration

/**
 * Computes swoop metrics from the canopy-to-landing dataPoints of a flight.
 *
 *   1. Candidate dive points are local vertical-speed maxima, fast enough to be a dive (`minSwoopVerticalSpeed`) and
 *      low enough to be a swoop (`maxSwoopHeight` AGL).
 *   2. Episode validation: a candidate is the dive point only if the flight from the peak to landing is a continuous
 *      descent, the vertical speed decays towards the ground without a sustained recovery above a fraction of the peak
 *      speed (the recovery signature of a manoeuvre), and the peak carries meaningful height above the ground
 *      (`minSwoopDescent`).
 *   3. The remaining events are derived around the dive point with walks: turn initiation by walking backward past the
 *      pull threshold while the descent is still steepening, rollout start by walking backward from 100 ft above the
 *      stop altitude while vertical speed strictly increases.
 *
 * All reported altitudes are AGL, relative to the landing point's altitude.
 */
final class SwoopDataDetector private (config: SwoopConfig, preprocessor: Preprocessor) {

  /**
   * Detects swoop metrics from the flight data stream.
   */
  def detect[F[_]: Concurrent, A: PointReader](events: FlightEvents, stream: fs2.Stream[F, A]): F[Option[SwoopData]] =
    (events.canopy, events.landing)
      .mapN(detect(_, _, stream))
      .sequence
      .map(_.flatten)

  private def detect[F[_]: Concurrent, A: PointReader](
    canopy: EventPoint,
    landing: EventPoint,
    stream: fs2.Stream[F, A]
  ): F[Option[SwoopData]] =
    stream
      .through(preprocessor.preprocess)
      .zipWithIndex
      .drop(canopy.index)
      .take(landing.index - canopy.index + 1)
      .map(IndexedPoint.fromZip)
      .compile
      .toVector
      .map(computeSwoopMetrics)

  //  Swoop Detection  //

  private def computeSwoopMetrics(dataPoints: Vector[IndexedPoint]): Option[SwoopData] =
    for
      _           <- Option.when(dataPoints.length >= 2)(None)
      ground       = dataPoints.last.point.altitude
      diveIdx     <- findDivePoint(dataPoints, ground)
      turnInitIdx <- findTurnInitiation(dataPoints, diveIdx)
      rolloutIdx  <- findRolloutStart(dataPoints, turnInitIdx, ground)
      result       = buildSwoopData(dataPoints, turnInitIdx, rolloutIdx, ground)
    yield result

  /**
   * Candidate peaks and episode validation. Returns the dive point (bottom of the dive).
   */
  private def findDivePoint(dataPoints: Vector[IndexedPoint], ground: Double): Option[Int] =
    findCandidatePeaks(dataPoints, ground)
      .reverse
      .find(isContinuousDescent(dataPoints, ground, _))

  /**
   * Local vertical-speed maxima that are fast enough to be a dive and low enough to be a swoop.
   */
  private def findCandidatePeaks(dataPoints: Vector[IndexedPoint], ground: Double): Vector[Int] =
    (1 until dataPoints.length - 1)
      .toVector
      .filter { i =>
        val vz     = dataPoints(i).point.speed.vertical
        val vzPrev = dataPoints(i - 1).point.speed.vertical
        val vzNext = dataPoints(i + 1).point.speed.vertical
        val agl    = dataPoints(i).point.altitude - ground

        val isFastEnough = vz >= config.minSwoopVerticalSpeed
        val isLowEnough  = agl <= config.maxSwoopHeight
        val prevIsSlower = vz >= vzPrev
        val nextIsSlower = vz >= vzNext

        isFastEnough && isLowEnough && prevIsSlower && nextIsSlower
      }

  /**
   * Episode validation: the descent from the candidate peak must reach the ground without a sustained
   * recovery. A recovery is `sustainedRecoverySamples` consecutive points back above `recoveryFraction` of
   * the peak speed once the speed has dropped below it, the signature of a manoeuvre followed by renewed
   * canopy flight. The peak must also carry meaningful height above the ground, which rejects flare-level
   * speed bumps near touchdown.
   */
  private def isContinuousDescent(dataPoints: Vector[IndexedPoint], ground: Double, peakIdx: Int): Boolean =
    val descentHeight =
      dataPoints(peakIdx).point.altitude - ground

    if descentHeight < config.minSwoopDescent then
      return false

    val recoveryLevel =
      config.recoveryFraction * dataPoints(peakIdx).point.speed.vertical

    @scala.annotation.tailrec
    def loop(i: Int, droppedBelow: Boolean, aboveCount: Int): Boolean =
      if i >= dataPoints.length then
        return true

      val vz = dataPoints(i).point.speed.vertical

      if vz < recoveryLevel then
        return loop(i + 1, droppedBelow = true, aboveCount = 0)

      val aboveCounteNext =
        if droppedBelow then aboveCount + 1 else aboveCount

      val isSustainedRecovery =
        droppedBelow && aboveCount + 1 >= config.sustainedRecoverySamples

      if isSustainedRecovery then
        return false

      loop(i + 1, droppedBelow, aboveCounteNext)

    loop(peakIdx + 1, droppedBelow = false, aboveCount = 0)

  /**
   * Walk backward from the dive point to the first point below the pull threshold, falling back to the minimum-descent
   * point if the threshold is never crossed, then continue backward while the descent is still steepening.
   */
  private def findTurnInitiation(dataPoints: Vector[IndexedPoint], diveIdx: Int): Option[Int] =
    Option.unless(diveIdx <= 0) {
      val speedAt =
        (j: Int) => dataPoints(j).point.speed.vertical

      @scala.annotation.tailrec
      def walkBack(i: Int)(pred: Int => Boolean): Int =
        if i > 0 && pred(i) then walkBack(i - 1)(pred) else i

      val crossed =
        walkBack(diveIdx)(i => speedAt(i) >= config.pullThreshold)

      val afterPull =
        if speedAt(crossed) >= config.pullThreshold then
          (0 to diveIdx).minBy(speedAt)
        else
          crossed

      walkBack(afterPull)(i => isSteepening(dataPoints, i))
    }

  /**
   * True when the vertical speed is still increasing at point `i` (the dive is steepening there)
   */
  private def isSteepening(dataPoints: Vector[IndexedPoint], i: Int): Boolean =
    val dt = Duration.between(dataPoints(i - 1).point.time, dataPoints(i).point.time).toMillis / 1000.0

    val vz     = dataPoints(i).point.speed.vertical
    val vzPrev = dataPoints(i - 1).point.speed.vertical

    val timeAdvances = dt > 0
    val vzIsRising   = (vz - vzPrev) / dt > 0

    timeAdvances && vzIsRising

  /**
   * From the turn initiation forward, find the first point below the rollout window (100 ft above the stop altitude),
   * then walk backward while vertical speed strictly increases. The last index before the increase stops is the
   * maximum-descent-rate point - the rollout start. Starting the walk above the flare zone bypasses the near-landing
   * noise.
   */
  private def findRolloutStart(dataPoints: Vector[IndexedPoint], turnInitIdx: Int, ground: Double): Option[Int] =
    @scala.annotation.tailrec
    def walkBack(i: Int, moved: Boolean): (Int, Boolean) =
      val prevIsFaster = 
        dataPoints(i - 1).point.speed.vertical > dataPoints(i).point.speed.vertical

      if i > 0 && prevIsFaster then
        walkBack(i - 1, moved = true)
      else
        (i, moved)

    val rolloutAltitude = ground + config.rolloutWindow
    val startIdx        = dataPoints.indexWhere(_.point.altitude < rolloutAltitude, turnInitIdx)

    Option
      .when(turnInitIdx < dataPoints.length - 1 && startIdx > 0) {
        walkBack(startIdx, moved = false)
      }
      .flatMap { (end, moved) =>
        Option.when(moved)(end)
      }

  //  Metrics Computation  //

  /** Assembles all swoop metrics; event altitudes are reported AGL (relative to the landing altitude). */
  private def buildSwoopData(
    dataPoints: Vector[IndexedPoint],
    turnInitIdx: Int,
    rolloutIdx: Int,
    ground: Double
  ): SwoopData =
    val turnSpeeds =
      dataPoints
        .slice(turnInitIdx, rolloutIdx + 1)
        .map(_.point.speed)

    val speeds =
      dataPoints
        .view
        .map(_.point.speed)
        .to(Vector)

    // Max vertical/total speed over [turn initiation, landing); max horizontal over [rollout, landing).
    val afterTurn    = speeds.drop(turnInitIdx)
    val afterRollout = speeds.drop(rolloutIdx)

    val turnInit = dataPoints(turnInitIdx)
    val rollout  = dataPoints(rolloutIdx)

    val turnInitAgl = turnInit.point.altitude - ground
    val rolloutAgl  = rollout.point.altitude - ground

    val degreesOfRotation = Heading.cumulativeHeadingChange(turnSpeeds, config.minHorizontalSpeed)

    SwoopData(
      turnInitiation = EventPoint(turnInit.idx, turnInitAgl),
      rolloutStart = EventPoint(rollout.idx, rolloutAgl),
      maxVerticalSpeed = afterTurn.map(_.vertical).max,
      maxTotalSpeed = afterTurn.map(_.magnitude).max,
      maxHorizontalSpeed = afterRollout.map(_.horizontal).max,
      degreesOfRotation = degreesOfRotation,
      timeToExecuteTurn = Duration.between(turnInit.point.time, rollout.point.time),
    )

}

object SwoopDataDetector {

  def apply(config: DetectionConfig): SwoopDataDetector =
    new SwoopDataDetector(config.swoop, PreprocessData(config))

  def apply(config: SwoopConfig, preprocessor: Preprocessor): SwoopDataDetector =
    new SwoopDataDetector(config, preprocessor)

}
