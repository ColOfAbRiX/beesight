package com.colofabrix.scala.beesight.output.chart

import com.colofabrix.scala.beesight.*
import com.colofabrix.scala.beesight.IOConfig
import com.colofabrix.scala.beesight.config.Config
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.detection.readers.*
import com.colofabrix.scala.beesight.pipeline.Stage
import java.nio.file.{ Files, Path }

/**
 * Generates an interactive HTML chart showing the flight traces with toggleable lines and the detected points.
 */
final private[beesight] class ChartGenerator private (config: Config) {

  /**
   * Returns a pipe that generates an HTML chart from the stream of points, enriched with the flight metrics and the
   * swoop analysis when they are available.
   */
  def chartPipe[A: PointReader](
    chartHtml: Path,
    metrics: Option[Vector[FlightMetric]],
    swoop: Option[SwoopData],
  ): fs2.Pipe[IOConfig, (FlightEvents, A), Nothing] =
    stream =>
      if config.command.stages.contains(Stage.Charts) && !config.global.dryRun then
        stream
          .fold(ChartState.empty[A])(updateChartState[A])
          .evalMap { state =>
            for
              _       <- IOConfig.blocking(Files.createDirectories(chartHtml.getParent))
              fileName = chartHtml.getFileName.toString.stripSuffix(".html") + ".csv"
              html     = ChartHtml.build(state, metrics, swoop, fileName)
              _       <- IOConfig.blocking(Files.writeString(chartHtml, html))
            yield ()
          }
          .drain
      else
        stream.drain

  private def updateChartState[A: PointReader](state: ChartState[A], data: (FlightEvents, A)): ChartState[A] =
    val (events, point) = data
    ChartState(
      times = state.times :+ point.time,
      altitudes = state.altitudes :+ point.altitude,
      velDs = state.velDs :+ point.speed.vertical,
      stages = events,
    )

}

private[beesight] object ChartGenerator {

  def apply(config: Config): ChartGenerator =
    new ChartGenerator(config)

}
