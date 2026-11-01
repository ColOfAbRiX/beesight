package com.colofabrix.scala.beesight.pipeline

import cats.effect.IO
import cats.syntax.all.*
import com.colofabrix.scala.beesight.*
import com.colofabrix.scala.beesight.IOConfig
import com.colofabrix.scala.beesight.detection.metrics.FlightMetricsCalculator
import com.colofabrix.scala.beesight.detection.model.FlightMetric
import com.colofabrix.scala.beesight.detection.readers.*

/**
 * Runs the per-point metrics stage over the whole input. The metrics are a pure function of the input and the
 * configuration, so they are computed fresh every time: the metadata file caches only the detection results.
 */
object MetricsStage {

  final case class ComputedMetrics(
    metrics: Option[Vector[FlightMetric]],
  )

  def run[A: PointReader: GpsReader](points: Vector[A]): IOConfig[ComputedMetrics] =
    IOConfig
      .ask
      .flatMap { config =>
        val stages = config.command.resolvedStages

        if stages.wants(Stage.Metrics) || stages.wants(Stage.Charts) then
          fs2.Stream
            .emits[IO, A](points)
            .through(FlightMetricsCalculator(config.detectionConfig.metrics).compute[IO, A])
            .compile
            .toVector
            .to[IOConfig]
            .map(computed => ComputedMetrics(Option.when(computed.nonEmpty)(computed)))
        else
          ComputedMetrics(None).pure[IOConfig]
      }

}
