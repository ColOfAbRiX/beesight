package com.colofabrix.scala.beesight.swoop

import cats.effect.kernel.Concurrent
import cats.syntax.all.*
import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.math.Heading
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.preprocess.*
import java.time.Duration

/**
 * Computes swoop metrics from the canopy-to-landing dataPoints of a flight.
 *
 * The detection follows the gSwoop semantics (see `gswoop/NOTES.md` and `detection_algorithm.md`) with an
 * additional episode-validation step that discriminates real swoops from canopy manoeuvres:
 *
 *   1. Candidate dive points are local vertical-speed maxima, fast enough to be a dive
 *      (`minSwoopVerticalSpeed`) and low enough to be a swoop (`maxSwoopHeight` AGL — gSwoop's 1000 ft cap).
 *   2. Episode validation: a candidate is the dive point only if the flight from the peak to landing is a
 *      continuous descent — the vertical speed decays towards the ground without a sustained recovery above a
 *      fraction of the peak speed (the recovery signature of a manoeuvre), and the peak carries meaningful
 *      height above the ground (`minSwoopDescent`).
 *   3. The remaining events are derived around the dive point with gSwoop's walks: turn initiation by
 *      walking backward past the pull threshold while the descent is still steepening (§6.3), rollout start
 *      by walking backward from 100 ft above the stop altitude while vertical speed strictly increases (§6.5).
 *
 * All reported altitudes are AGL, relative to the landing point's altitude. Detection runs on despiked but
 * unsmoothed data: gSwoop applies no filtering before detection, and smoothing was observed to flatten the
 * true peak and shift event indices.
 */
final class SwoopDataDetector private (config: DetectionConfig, preprocessor: Preprocessor) {

  /** gSwoop §6.3: descent rate below which the dive is considered over (25 mph), in m/s. */
  private val PullThreshold = 11.176

  /** gSwoop §6.5: rollout search starts this far above the stop altitude (100 ft), in metres. */
  private val RolloutWindow = 30.48

  /** Consecutive samples above the recovery level that mark a manoeuvre recovery. */
  private val SustainedRecoverySamples = 3

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
      _           <- Option.when(dataPoints.length < 2)(None)
      ground       = dataPoints.last.point.altitude
      diveIdx     <- findDivePoint(dataPoints, ground)
      turnInitIdx <- findTurnInitiation(dataPoints, diveIdx)
      rolloutIdx  <- findRolloutStart(dataPoints, turnInitIdx, ground)
      result       = buildSwoopData(dataPoints, turnInitIdx, rolloutIdx, ground)
    yield result

  /** Steps 1+2: candidate peaks and episode validation. Returns the dive point (bottom of the dive). */
  private def findDivePoint(dataPoints: Vector[IndexedPoint], ground: Double): Option[Int] =
    candidatePeaks(dataPoints, ground)
      .reverse
      .find(isContinuousDescent(dataPoints, ground, _))

  /**
   * Local vertical-speed maxima that are fast enough to be a dive and low enough to be a swoop.
   */
  private def candidatePeaks(dataPoints: Vector[IndexedPoint], ground: Double): Vector[Int] =
    (1 until dataPoints.length - 1).filter { i =>
      val vz  = dataPoints(i).point.speed.vertical
      val agl = dataPoints(i).point.altitude - ground
      vz >= config.swoop.minSwoopVerticalSpeed &&
      vz >= dataPoints(i - 1).point.speed.vertical &&
      vz >= dataPoints(i + 1).point.speed.vertical &&
      agl <= config.swoop.maxSwoopHeight
    }.toVector

  /**
   * Episode validation: the descent from the candidate peak must reach the ground without a sustained
   * recovery. A recovery is `SustainedRecoverySamples` consecutive points back above `recoveryFraction` of
   * the peak speed once the speed has dropped below it — the signature of a manoeuvre followed by renewed
   * canopy flight. The peak must also carry meaningful height above the ground, which rejects flare-level
   * speed bumps near touchdown.
   */
  private def isContinuousDescent(dataPoints: Vector[IndexedPoint], ground: Double, peakIdx: Int): Boolean =
    val descentHeight = dataPoints(peakIdx).point.altitude - ground

    if descentHeight < config.swoop.minSwoopDescent then 
      false
    else
      val recoveryLevel = config.swoop.recoveryFraction * dataPoints(peakIdx).point.speed.vertical

      @scala.annotation.tailrec
      def loop(i: Int, droppedBelow: Boolean, aboveCount: Int): Boolean =
        if i >= dataPoints.length then true
        else
          val vz = dataPoints(i).point.speed.vertical
          if vz < recoveryLevel then loop(i + 1, droppedBelow = true, aboveCount = 0)
          else if droppedBelow && aboveCount + 1 >= SustainedRecoverySamples then false
          else loop(i + 1, droppedBelow, if droppedBelow then aboveCount + 1 else aboveCount)

      loop(peakIdx + 1, droppedBelow = false, aboveCount = 0)

  /**
   * gSwoop §6.3 (simplified): walk backward from the dive point to the first point below the pull
   * threshold, falling back to the minimum-descent point if the threshold is never crossed, then continue
   * backward while the descent is still steepening (vertical speed still increasing in forward time).
   */
  private def findTurnInitiation(dataPoints: Vector[IndexedPoint], diveIdx: Int): Option[Int] =
    if diveIdx <= 0 then None
    else
      var i = diveIdx
      while i > 0 && dataPoints(i).point.speed.vertical >= PullThreshold do i -= 1

      if dataPoints(i).point.speed.vertical >= PullThreshold then
        i = (0 to diveIdx).minBy(j => dataPoints(j).point.speed.vertical)

      while i > 0 && isSteepening(dataPoints, i) do i -= 1

      Some(i)

  /** True when the vertical speed is still increasing at point `i` (the dive is steepening there). */
  private def isSteepening(dataPoints: Vector[IndexedPoint], i: Int): Boolean =
    val dt = Duration.between(dataPoints(i - 1).point.time, dataPoints(i).point.time).toMillis / 1000.0
    dt > 0 && (dataPoints(i).point.speed.vertical - dataPoints(i - 1).point.speed.vertical) / dt > 0

  /**
   * gSwoop §6.5: from the turn initiation forward, find the first point below the rollout window (100 ft
   * above the stop altitude), then walk backward while vertical speed strictly increases. The last index
   * before the increase stops is the maximum-descent-rate point — the rollout start. Starting the walk
   * above the flare zone bypasses the near-landing noise.
   */
  private def findRolloutStart(dataPoints: Vector[IndexedPoint], turnInitIdx: Int, ground: Double): Option[Int] =
    if turnInitIdx >= dataPoints.length - 1 then 
      None
    else
      val rolloutAltitude = ground + RolloutWindow
      val startIdx        = dataPoints.indexWhere(_.point.altitude < rolloutAltitude, turnInitIdx)
      if startIdx <= 0 then None
      else
        var i     = startIdx
        var moved = false
        while i > 0 && dataPoints(i - 1).point.speed.vertical > dataPoints(i).point.speed.vertical do
          i -= 1
          moved = true
        Option.when(moved)(i)

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

    // gSwoop §6.9: max vertical/total speed over [turn initiation, landing); max horizontal over [rollout, landing).
    val afterTurn    = dataPoints.drop(turnInitIdx).map(_.point.speed)
    val afterRollout = dataPoints.drop(rolloutIdx).map(_.point.speed)

    val turnInit = dataPoints(turnInitIdx)
    val rollout  = dataPoints(rolloutIdx)

    SwoopData(
      turnInitiation = EventPoint(turnInit.idx, turnInit.point.altitude - ground),
      rolloutStart = EventPoint(rollout.idx, rollout.point.altitude - ground),
      maxVerticalSpeed = afterTurn.map(_.vertical).max,
      maxTotalSpeed = afterTurn.map(_.magnitude).max,
      maxHorizontalSpeed = afterRollout.map(_.horizontal).max,
      degreesOfRotation = Heading.cumulativeHeadingChange(turnSpeeds, config.swoop.minHorizontalSpeed),
      timeToExecuteTurn = Duration.between(turnInit.point.time, rollout.point.time),
    )

}

object SwoopDataDetector {

  def apply(config: DetectionConfig): SwoopDataDetector =
    new SwoopDataDetector(config, PreprocessData(config))

  def apply(config: DetectionConfig, preprocessor: Preprocessor): SwoopDataDetector =
    new SwoopDataDetector(config, preprocessor)

}
