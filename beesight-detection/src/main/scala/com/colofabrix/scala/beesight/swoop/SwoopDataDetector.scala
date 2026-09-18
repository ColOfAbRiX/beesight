package com.colofabrix.scala.beesight.swoop

import cats.effect.kernel.Concurrent
import cats.syntax.functor.*
import com.colofabrix.scala.beesight.config.{ DetectionConfig, SwoopConfig }
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.swoop.model.PointSeries
import java.time.Duration

/**
 * Computes swoop metrics from the flight data stream:
 *
 *   1. Pattern entry: the first crossing below the pattern-entry altitude above the detected ground.
 *   2. Dive point: the first point achieving the maximum descent rate below the altitude cap.
 *   3. Top of turn: the walk-back from the dive point - pull threshold, deceleration boundary, steepening
 *      accumulator.
 *   4. Stop: the end of motion after the dive - the first slow point fixes the swoop heading, then the stop is the
 *      first point deviating more than the heading deviation from it or slowing below the end threshold.
 *   5. Rollout: the maximum-descent point within the rollout window above the stop altitude.
 *   6. Bottom of turn: the first point above the bottom altitude after the rollout, falling back to the descent-rate
 *      crossing, then to the point after the rollout.
 *   7. Metrics: maximum speeds over the detection windows, the signed per-sample turn-angle sum, and the turn
 *      direction.
 *
 * When a stage cannot find its event, no swoop is reported at all.
 *
 * All reported altitudes are AGL relative to the detected ground.
 */
final class SwoopDataDetector private (config: SwoopConfig) {

  /**
   * Detects swoop metrics from the flight data stream.
   */
  def detect[F[_]: Concurrent, A: PointReader](events: FlightEvents, stream: fs2.Stream[F, A]): F[Option[SwoopData]] =
    stream
      .zipWithIndex
      .map(IndexedPoint.fromZip)
      .compile
      .toVector
      .map(computeSwoopMetrics(_, events))

  private def computeSwoopMetrics(dataPoints: Vector[IndexedPoint], events: FlightEvents): Option[SwoopData] =
    for
      series       <- Option.unless(dataPoints.length < 2)(PointSeries(dataPoints))
      ground        = SwoopGround.detect(series, events.landing)
      patternEntry <- SwoopCascade.findPatternEntry(series, ground)
      dive         <- SwoopCascade.findDivePoint(series, patternEntry, ground)
      topOfTurn     = SwoopCascade.findTopOfTurn(series, dive, patternEntry, config.pullThreshold)
      stop         <- SwoopCascade.findStop(series, dive)
      rollout      <- SwoopCascade.findRolloutStart(series, topOfTurn, stop, config.rolloutWindow)
      bottom        = SwoopCascade.findBottomOfTurn(series, rollout, stop) if events.canopy.isDefined
      result        = buildSwoopData(series, dive, topOfTurn, rollout, bottom, stop, ground)
    yield result

  private def buildSwoopData(
    series: PointSeries,
    diveIdx: Int,
    topOfTurn: Int,
    rolloutIdx: Int,
    bottomIdx: Int,
    stopIdx: Int,
    ground: Double
  ): SwoopData =
    val agl  = (j: Int) => series.altitude(j) - ground
    val last = series.length - 1

    val maxSpeeds         = SwoopMetrics.maxSpeeds(series, diveIdx, topOfTurn, rolloutIdx, stopIdx)
    val degreesOfRotation = SwoopMetrics.turnDegrees(series, topOfTurn, Math.min(bottomIdx, last))

    SwoopData(
      turnInitiation = EventPoint(series(topOfTurn).idx, agl(topOfTurn)),
      rolloutStart = EventPoint(series(rolloutIdx).idx, agl(rolloutIdx)),
      maxVerticalSpeed = SpeedAtAltitude(maxSpeeds.vertical.speed, agl(maxSpeeds.vertical.index)),
      maxTotalSpeed = SpeedAtAltitude(maxSpeeds.total.speed, agl(maxSpeeds.total.index)),
      maxHorizontalSpeed = SpeedAtAltitude(maxSpeeds.horizontal.speed, agl(maxSpeeds.horizontal.index)),
      degreesOfRotation = degreesOfRotation,
      turnDirection = TurnDirection.fromRotation(degreesOfRotation),
      timeToExecuteTurn = Duration.between(series.time(topOfTurn), series.time(rolloutIdx)),
    )

}

object SwoopDataDetector {

  def apply(config: DetectionConfig): SwoopDataDetector =
    new SwoopDataDetector(config.swoop)

}
