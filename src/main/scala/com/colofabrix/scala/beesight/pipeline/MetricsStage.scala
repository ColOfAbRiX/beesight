package com.colofabrix.scala.beesight.pipeline

import cats.effect.IO
import cats.syntax.all.*
import com.colofabrix.scala.beesight.*
import com.colofabrix.scala.beesight.IOConfig
import com.colofabrix.scala.beesight.config.Config
import com.colofabrix.scala.beesight.detection.metrics.FlightMetricsCalculator
import com.colofabrix.scala.beesight.detection.model.FlightMetric
import com.colofabrix.scala.beesight.detection.readers.*
import com.colofabrix.scala.beesight.metadata.*
import com.colofabrix.scala.beesight.metadata.ConfigHash.*
import com.colofabrix.scala.beesight.metadata.StoredConversions.*

/**
 * Runs the per-point metrics stage over the whole input, reusing the cached metrics when the sidecar section is
 * fresh. The metrics are independent of the cut.
 */
object MetricsStage {

  final case class Result(
    metrics: Option[Vector[FlightMetric]],
    section: Option[MetricsSection],
  )

  def run[A: PointReader: GpsReader](points: Vector[A], sidecar: Sidecar): IOConfig[Result] =
    IOConfig
      .ask
      .flatMap { config =>
        val stages = config.command.resolvedStages

        if stages.wants(Stage.Metrics) || stages.wants(Stage.Charts) then
          val configHash = config.detectionConfig.metrics.configHash

          sidecar
            .sections
            .metrics
            .filter(_.configHash === configHash)
            .map(fromSidecar)
            .getOrElse(compute[A](config, points, configHash))
        else
          Result(None, None).pure[IOConfig]
      }

  private def fromSidecar(section: MetricsSection): IOConfig[Result] =
    Result(Some(section.metrics.map(_.toFlightMetric).toVector), Some(section)).pure[IOConfig]

  private def compute[A: PointReader: GpsReader](
    cfg: Config,
    points: Vector[A],
    configHash: ConfigHash,
  ): IOConfig[Result] =
    fs2.Stream
      .emits[IO, A](points)
      .through(FlightMetricsCalculator(cfg.detectionConfig.metrics).compute[IO, A])
      .compile
      .toVector
      .to[IOConfig]
      .map { computed =>
        val metrics = Option.when(computed.nonEmpty)(computed)
        Result(metrics, metrics.map(m => MetricsSection(configHash, m.map(_.toStored).toList)))
      }

}
