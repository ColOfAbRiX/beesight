package com.colofabrix.scala.beesight.output
import com.colofabrix.scala.beesight.model.WorkingPaths

import cats.syntax.all.*
import com.colofabrix.scala.beesight.*
import com.colofabrix.scala.beesight.IOConfig
import com.colofabrix.scala.beesight.config.Config
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.detection.readers.*
import com.colofabrix.scala.beesight.output.MetricsCsvWriter.given
import com.colofabrix.scala.beesight.output.chart.ChartGenerator
import com.colofabrix.scala.beesight.pipeline.Stage
import fs2.data.csv.CsvRowEncoder
import java.nio.file.{ Files, Path }

/**
 * Produces every output file of a stage run: the processed CSV, the chart, the console summary, the metrics CSV and
 * the swoop report. Each output enables itself based on the requested stages and the dry-run option.
 */
object StageOutputs {

  def run[A: PointReader: CsvRowEncoder[*, String]](
    retained: Vector[(FlightEvents, A)],
    finalEvents: FlightEvents,
    metrics: Option[Vector[FlightMetric]],
    swoop: Option[SwoopData],
    paths: WorkingPaths,
  ): IOConfig[Unit] =
    for
      config <- IOConfig.ask
      _      <- processedOutputs[A](config, retained, metrics, swoop, paths)
      _      <- metricsCsv(config, finalEvents, metrics, paths)
      _      <- swoopJson(config, swoop, paths)
    yield ()

  private def processedOutputs[A: PointReader: CsvRowEncoder[*, String]](
    cfg: Config,
    retained: Vector[(FlightEvents, A)],
    metrics: Option[Vector[FlightMetric]],
    swoop: Option[SwoopData],
    paths: WorkingPaths,
  ): IOConfig[Unit] =
    fs2.Stream
      .emits(retained)
      .broadcastThrough(
        OutputCsvWriter(cfg).writePipe(paths.outputFile),
        ChartGenerator(cfg).chartPipe[A](paths.chartHtml, metrics, swoop),
        SummaryOutput(cfg).printStagesPipe,
      )
      .compile
      .drain

  private def metricsCsv(
    cfg: Config,
    finalEvents: FlightEvents,
    metrics: Option[Vector[FlightMetric]],
    paths: WorkingPaths,
  ): IOConfig[Unit] =
    metrics.traverse_ { computed =>
      fs2.Stream
        .emits[IOConfig, (FlightEvents, FlightMetric)](computed.map(finalEvents -> _))
        .through(MetricsCsvWriter(cfg).writePipe(paths.metricsCsv))
        .compile
        .drain
    }

  private def swoopJson(
    cfg: Config,
    swoop: Option[SwoopData],
    paths: WorkingPaths,
  ): IOConfig[Unit] =
    swoop.traverse_ { data =>
      IOConfig
        .blocking(Files.writeString(paths.swoopJson, SwoopReportWriter.toJson(data)))
        .whenA(cfg.command.stages.contains(Stage.Swoop) && !cfg.global.dryRun)
    }

}
