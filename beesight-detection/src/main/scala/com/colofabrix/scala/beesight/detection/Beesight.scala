package com.colofabrix.scala.beesight.detection

import cats.effect.kernel.Concurrent
import cats.syntax.all.*
import com.colofabrix.scala.beesight.config.*
import com.colofabrix.scala.beesight.detection.Beesight.*
import com.colofabrix.scala.beesight.jumpevents.JumpEventsDetector
import com.colofabrix.scala.beesight.metrics.FlightMetricsCalculator
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.readers.*
import com.colofabrix.scala.beesight.swoop.SwoopDataDetector
import cats.data.OptionT

// FIXME: This is the main entry point for clients of the library. Add full documentation
final class Beesight private (config: BeesightConfig) {

  private val jumpEventsDetector =
    JumpEventsDetector(config.jumpevents)

  private val swoopDetector =
    SwoopDataDetector(config.swoop)

  private val flightMetricsCalculator =
    FlightMetricsCalculator(config.metrics)

  def jumpPoints[F[_]: Concurrent, A: PointReader](stream: fs2.Stream[F, A]): F[FlightEvents] =
    stream
      .through(jumpEventsDetector.detect[F, A])
      .last
      .compile
      .toList
      .map {
        _.lastOption.flatten.getOrElse(FlightEvents.empty)
      }

  def swoopMetrics[F[_]: Concurrent, A: PointReader](stream: fs2.Stream[F, A]): F[Option[SwoopData]] =
    for
      events <- jumpPoints(stream)
      result <- swoopDetector.detect[F, A](events, stream)
    yield result

  def flightMetrics[F[_], A: PointReader: GpsReader]: fs2.Pipe[F, A, FlightMetric] =
    flightMetricsCalculator.compute[F, A]

  // FIXME: Ensemble is horrible. Find the right name
  def ensemble[F[_]: Concurrent, A: PointReader: GpsReader]: fs2.Pipe[F, A, Ensemble] =
    stream =>
      val flightEventsStream = stream.through(jumpEventsDetector.detect[F, A])
      val metricsStream      = stream.through(flightMetricsCalculator.compute[F, A])

      (flightEventsStream zip metricsStream)
        .zipWithIndex
        .evalMap {
          case ((events, metrics), idx) =>
            OptionT
              .fromOption[F](events.canopy)
              .flatMapF {_ => 
                swoopDetector.detect[F, A](events, stream.take(idx + 1))
              }
              .value
              .map(Ensemble(events, metrics, _))
        }

}

object Beesight {

  // FIXME: Find a better and more representative name
  final case class Ensemble(
    events: FlightEvents,
    metrics: FlightMetric,
    swoopData: Option[SwoopData],
  ) {
    export metrics.*
  }

  def apply(config: BeesightConfig): Beesight =
    new Beesight(config)

}
