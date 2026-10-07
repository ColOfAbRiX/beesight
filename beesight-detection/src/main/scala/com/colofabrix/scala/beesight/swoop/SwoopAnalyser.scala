package com.colofabrix.scala.beesight.swoop

import cats.effect.kernel.Concurrent
import cats.syntax.functor.*
import com.colofabrix.scala.beesight.config.{ Altitude, SwoopConfig }
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.readers.*
import com.colofabrix.scala.beesight.swoop.computations.*
import com.colofabrix.scala.beesight.swoop.model.*
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
final class SwoopAnalyser private (config: SwoopConfig) {

  private val groundDetector =
    SwoopGround(config)

  /**
   * Analyses the flight data stream: detects the swoop and computes its metrics.
   */
  def analyse[F[_]: Concurrent, A: PointReader](events: FlightEvents, stream: fs2.Stream[F, A]): F[Option[SwoopData]] =
    stream
      .zipWithIndex
      .through(cutStream(events))
      .map(IndexedPoint.fromZip)
      .compile
      .toVector
      .map(computeSwoopMetrics(_, events))

  private def cutStream[F[_], A: PointReader](events: FlightEvents): fs2.Pipe[F, (A, Long), (A, Long)] =
    stream =>
      val cutStart =
        events
          .freefall
          .map { fall =>
            fall.time.minus(config.stream.beforeCanopyMargin)
          }

      stream.dropWhile {
        case (point, _) => cutStart.exists(point.time.isBefore)
      }

  private def computeSwoopMetrics(dataPoints: Vector[IndexedPoint], events: FlightEvents): Option[SwoopData] =
    for
      series       <- Option.unless(dataPoints.length < 2)(PointSeries(dataPoints))
      ground        = groundDetector.detect(series, events.landing)
      patternEntry <- SwoopPatternEntry.find(series, ground, config)
      dive         <- SwoopDivePoint.find(series, patternEntry, ground, config)
      topOfTurn     = SwoopTopOfTurn.find(series, dive, patternEntry, config)
      stop         <- SwoopStop.find(series, dive, config)
      rollout      <- SwoopRolloutStart.find(series, topOfTurn, stop, config)
      bottom        = SwoopBottomOfTurn.find(series, rollout, stop, config)
      swoopData     = buildSwoopData(series, dive, topOfTurn, rollout, bottom, stop, ground)
      result       <- Option.when(events.canopy.isDefined)(swoopData)
    yield result

  private def buildSwoopData(
    series: PointSeries,
    diveIdx: Int,
    topOfTurn: Int,
    rolloutIdx: Int,
    bottomIdx: Int,
    stopIdx: Int,
    ground: Altitude
  ): SwoopData =
    val agl  = (j: Int) => series.altitude(j) - ground.value
    val last = series.length - 1

    val maxSpeeds =
      SwoopMetrics.maxSpeeds(series, diveIdx, topOfTurn, rolloutIdx, stopIdx)

    val degreesOfRotation =
      SwoopMetrics.turnDegrees(series, topOfTurn, Math.min(bottomIdx, last), config)

    val turnInitiation =
      EventPoint(series(topOfTurn).idx, agl(topOfTurn), series.time(topOfTurn), series(topOfTurn).speed)

    val rolloutStart =
      EventPoint(series(rolloutIdx).idx, agl(rolloutIdx), series.time(rolloutIdx), series(rolloutIdx).speed)

    val maxVerticalSpeed =
      SpeedAtAltitude(
        series(maxSpeeds.vertical.index).idx,
        maxSpeeds.vertical.speed,
        agl(maxSpeeds.vertical.index)
      )

    val maxTotalSpeed =
      SpeedAtAltitude(
        series(maxSpeeds.total.index).idx,
        maxSpeeds.total.speed,
        agl(maxSpeeds.total.index)
      )

    val maxHorizontalSpeed =
      SpeedAtAltitude(
        series(maxSpeeds.horizontal.index).idx,
        maxSpeeds.horizontal.speed,
        agl(maxSpeeds.horizontal.index)
      )

    val turnDirection =
      TurnDirection.fromRotation(degreesOfRotation)

    val timeToExecuteTurn =
      Duration.between(series.time(topOfTurn), series.time(rolloutIdx))

    SwoopData(
      turnInitiation = turnInitiation,
      rolloutStart = rolloutStart,
      maxVerticalSpeed = maxVerticalSpeed,
      maxTotalSpeed = maxTotalSpeed,
      maxHorizontalSpeed = maxHorizontalSpeed,
      degreesOfRotation = degreesOfRotation,
      turnDirection = turnDirection,
      timeToExecuteTurn = timeToExecuteTurn,
    )

}

object SwoopAnalyser {

  def apply(config: SwoopConfig): SwoopAnalyser =
    new SwoopAnalyser(config)

}
