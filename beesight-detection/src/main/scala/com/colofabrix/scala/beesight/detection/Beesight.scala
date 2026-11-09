package com.colofabrix.scala.beesight.detection

import cats.data.OptionT
import cats.effect.kernel.Concurrent
import cats.syntax.all.*
import com.colofabrix.scala.beesight.detection.Beesight.*
import com.colofabrix.scala.beesight.detection.config.*
import com.colofabrix.scala.beesight.detection.jumpevents.JumpEventsDetector
import com.colofabrix.scala.beesight.detection.metrics.FlightMetricsCalculator
import com.colofabrix.scala.beesight.detection.metrics.computations.MetricsGround
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.detection.readers.*
import com.colofabrix.scala.beesight.detection.swoop.SwoopAnalyser

/**
 * Main entry point for clients of the library.
 */
final class Beesight private (config: BeesightConfig) {

  private val defaultJumpEventsDetector =
    JumpEventsDetector(config.jumpevents)

  private val defaultSwoopAnalyser =
    SwoopAnalyser(config.swoop)

  private val defaultFlightMetricsCalculator =
    FlightMetricsCalculator(config.metrics)

  /**
   * Detects the flight event points of a single jump: takeoff, freefall, canopy and landing.
   */
  def jumpPoints[F[_]: Concurrent, A: PointReader](stream: fs2.Stream[F, A]): F[FlightEvents] =
    stream
      .through(defaultJumpEventsDetector.detect[F, A])
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
      result <- defaultSwoopAnalyser.analyse[F, A](events, stream)
    yield result

  /**
   * Computes the per-point flight metrics of a stream of GPS points.
   */
  def flightMetrics[F[_], A: PointReader: GpsReader]: fs2.Pipe[F, A, FlightMetric] =
    defaultFlightMetricsCalculator.compute[F, A]

  /**
   * Processes a jump end-to-end, emitting per-point metrics with the detected flight events and, when the jump includes
   * a swoop, its swoop data. The flight events are detected for the whole jump up front, so each point carries the
   * final events and its metric altitude is above ground level, resolved from the landing point.
   */
  def enrichedPoints[F[_]: Concurrent, A: PointReader: GpsReader]: fs2.Pipe[F, A, EnrichedPoint] =
    stream =>
      for
        events           <- fs2.Stream.eval(jumpPoints(stream))
        ground            = MetricsGround.detect(events.landing, config.metrics)
        metricsCalculator = FlightMetricsCalculator(config.metrics, ground)
        swoopAnalyser     = SwoopAnalyser(config.swoop, ground.some)
        (metrics, idx)   <- stream.through(metricsCalculator.compute[F, A]).zipWithIndex
        swoopData        <- fs2.Stream.eval(swoopAnalyser.analyse[F, A](events, stream.take(idx + 1)))
        result            = EnrichedPoint(events, metrics, swoopData)
      yield result
      
}

object Beesight {

  /**
   * All the information Beesight provides for each point: the flight events detected so far, the per-point metrics and,
   * when a swoop has been detected, its data.
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
