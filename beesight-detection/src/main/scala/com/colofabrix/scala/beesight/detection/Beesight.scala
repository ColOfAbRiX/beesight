package com.colofabrix.scala.beesight.detection

import cats.data.OptionT
import cats.effect.kernel.Concurrent
import cats.syntax.all.*
import com.colofabrix.scala.beesight.detection.config.*
import com.colofabrix.scala.beesight.detection.Beesight.*
import com.colofabrix.scala.beesight.detection.jumpevents.JumpEventsDetector
import com.colofabrix.scala.beesight.detection.metrics.FlightMetricsCalculator
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.detection.readers.*
import com.colofabrix.scala.beesight.detection.swoop.SwoopAnalyser

/**
 * Main entry point for clients of the library.
 */
final class Beesight private (config: BeesightConfig) {

  private val jumpEventsDetector =
    JumpEventsDetector(config.jumpevents)

  private val swoopAnalyser =
    SwoopAnalyser(config.swoop)

  private val flightMetricsCalculator =
    FlightMetricsCalculator(config.metrics)

  /**
   * Detects the flight event points of a single jump: takeoff, freefall, canopy and landing.
   */
  def jumpPoints[F[_]: Concurrent, A: PointReader](stream: fs2.Stream[F, A]): F[FlightEvents] =
    stream
      .through(jumpEventsDetector.detect[F, A])
      .last
      .compile
      .toList
      .map {
        _.lastOption.flatten.getOrElse(FlightEvents.empty)
      }

  /**
   * Computes the swoop metrics of a single jump, or `None` when the flight contains no swoop.
   */
  def swoopMetrics[F[_]: Concurrent, A: PointReader](stream: fs2.Stream[F, A]): F[Option[SwoopData]] =
    for
      events <- jumpPoints(stream)
      result <- swoopAnalyser.analyse[F, A](events, stream)
    yield result

  /**
   * Computes the per-point flight metrics of a stream of GPS points.
   */
  def flightMetrics[F[_], A: PointReader: GpsReader]: fs2.Pipe[F, A, FlightMetric] =
    flightMetricsCalculator.compute[F, A]

  /**
   * Processes a jump end-to-end, emitting per-point metrics with the detected flight events and, when the jump
   * includes a swoop, its swoop data.
   */
  def enrichedPoints[F[_]: Concurrent, A: PointReader: GpsReader]: fs2.Pipe[F, A, EnrichedPoint] =
    stream =>
      val flightEventsStream = stream.through(jumpEventsDetector.detect[F, A])
      val metricsStream      = stream.through(flightMetricsCalculator.compute[F, A])

      (flightEventsStream zip metricsStream)
        .zipWithIndex
        .evalMap {
          case ((events, metrics), idx) =>
            OptionT
              .fromOption[F](events.canopy)
              .flatMapF { _ =>
                swoopAnalyser.analyse[F, A](events, stream.take(idx + 1))
              }
              .value
              .map(EnrichedPoint(events, metrics, _))
        }

}

object Beesight {

  /**
   * All the information Beesight provides for each point: the flight events detected so far, the per-point metrics
   * and, when a swoop has been detected, its data.
   */
  final case class EnrichedPoint(
    events: FlightEvents,
    metrics: FlightMetric,
    swoopData: Option[SwoopData],
  ) {
    export metrics.*
  }

  def apply(config: BeesightConfig): Beesight =
    new Beesight(config)

}
