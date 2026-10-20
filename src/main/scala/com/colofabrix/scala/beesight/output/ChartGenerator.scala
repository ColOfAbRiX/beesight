package com.colofabrix.scala.beesight.output

import cats.syntax.all.*
import com.colofabrix.scala.beesight.*
import com.colofabrix.scala.beesight.config.Config
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.pipeline.Stage
import com.colofabrix.scala.beesight.detection.readers.*
import java.nio.file.*

/**
 * Generates interactive HTML charts showing flight data and detected stages.
 */
final private[beesight] class ChartGenerator private (config: Config) {

  /**
   * Returns a pipe that generates an HTML chart from the stream of OutputFlightPoints
   */
  def chartPipe[A: PointReader](outputFile: Path): fs2.Pipe[IOConfig, (FlightEvents, A), Nothing] =
    stream =>
      if config.command.stages.contains(Stage.Charts) && !config.global.dryRun then
        stream
          .zipWithIndex
          .fold(ChartState[A]()) {
            case (state, (point, idx)) => updateChartState(state, point, idx)
          }
          .evalMap { state =>
            for
              outputPath <- computeChartPath(outputFile)
              html        = createHtmlFromState(state)
              _          <- writeHtmlFile(html, outputPath)
            yield ()
          }
          .drain
      else
        stream.drain

  private def updateChartState[A: PointReader](
    state: ChartState[A],
    data: (FlightEvents, A),
    idx: Long,
  ): ChartState[A] =
    val (events, point) = data

    val sep = if idx == 0 then "" else ","

    // Update data builders
    val newIndices   = state.indices.append(sep).append(idx)
    val newAltitudes = state.altitudes.append(sep).append(point.altitude)
    val newVelDs     = state.velDs.append(sep).append(point.speed.vertical)

    // Detect phase transition
    val transition =
      if state.prevPhase != events.phase && events.phase != FlightPhase.BeforeTakeoff then
        Some(PhaseTransition(events.phase, idx, point.altitude))
      else
        None

    ChartState(
      indices = newIndices,
      altitudes = newAltitudes,
      velDs = newVelDs,
      transitions = state.transitions ++ transition.toList,
      prevPhase = events.phase,
      lastStages = extractStages(events),
    )

  private def extractStages[A](events: FlightEvents): FlightEvents =
    FlightEvents(
      takeoff = events.takeoff,
      freefall = events.freefall,
      canopy = events.canopy,
      landing = events.landing,
    )

  private def computeChartPath(outputFile: Path): IOConfig[Path] =
    val baseName =
      outputFile
        .getFileName
        .toString
        .replaceFirst("\\.[^.]+$", "")

    val chartFilePath =
      outputFile
        .resolveSibling(s"$baseName.html")
        .toAbsolutePath
        .normalize

    IOConfig
      .blocking(Files.createDirectories(chartFilePath.getParent))
      .as(chartFilePath)

  private def writeHtmlFile(html: String, outputPath: Path): IOConfig[Unit] =
    IOConfig.blocking {
      val _ = Files.writeString(outputPath, html)
    }

  private def createHtmlFromState[A](state: ChartState[A]): String =
    val indicesJs   = s"[${state.indices.result()}]"
    val altitudesJs = s"[${state.altitudes.result()}]"
    val velDsJs     = s"[${state.velDs.result()}]"
    val stages      = state.lastStages
    val markers     = createTransitionMarkersJs(state.transitions)

    s"""<!DOCTYPE html>
       |<html>
       |<head>
       |  <meta charset="utf-8">
       |  <title>Flight Analysis</title>
       |  <script src="https://cdn.plot.ly/plotly-2.27.0.min.js"></script>
       |  <style>
       |    body { margin: 0; padding: 20px; font-family: sans-serif; background: #f5f5f5; }
       |    h1 { color: #333; }
       |    #plot { width: 100%; height: 80vh; background: white; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
       |    .info { margin-top: 20px; padding: 15px; background: white; border-radius: 8px; }
       |    .info h3 { margin-top: 0; }
       |  </style>
       |</head>
       |<body>
       |  <h1>Flight Analysis</h1>
       |  <div id="plot"></div>
       |  <div class="info">
       |    <h3>Detected Flight Stages</h3>
       |    <ul>
       |      <li><strong>Takeoff:</strong> ${formatStage(stages.takeoff)}</li>
       |      <li><strong>Freefall:</strong> ${formatStage(stages.freefall)}</li>
       |      <li><strong>Canopy:</strong> ${formatStage(stages.canopy)}</li>
       |      <li><strong>Landing:</strong> ${formatStage(stages.landing)}</li>
       |    </ul>
       |  </div>
       |  <script>
       |    var altitudeTrace = {
       |      x: $indicesJs,
       |      y: $altitudesJs,
       |      name: 'Altitude (m)',
       |      type: 'scatter',
       |      mode: 'lines',
       |      line: {color: 'rgb(31, 119, 180)', width: 1.5}
       |    };
       |
       |    var velocityTrace = {
       |      x: $indicesJs,
       |      y: $velDsJs,
       |      name: 'Velocity Down (m/s)',
       |      type: 'scatter',
       |      mode: 'lines',
       |      line: {color: 'rgb(255, 127, 14)', width: 1},
       |      yaxis: 'y2'
       |    };
       |
       |    $markers
       |
       |    var layout = {
       |      title: 'Flight Data',
       |      xaxis: {title: 'Data Point Index'},
       |      yaxis: {title: 'Altitude (m)'},
       |      yaxis2: {
       |        title: 'Velocity Down (m/s)',
       |        overlaying: 'y',
       |        side: 'right'
       |      },
       |      legend: {x: 0.01, y: 0.99},
       |      hovermode: 'closest'
       |    };
       |
       |    var config = {responsive: true};
       |
       |    Plotly.newPlot('plot', [altitudeTrace, velocityTrace].concat(transitionMarkers), layout, config);
       |  </script>
       |</body>
       |</html>
       |""".stripMargin

  private def createTransitionMarkersJs(transitions: List[PhaseTransition]): String =
    val colorMap = Map(
      FlightPhase.Climbing      -> "rgb(44, 160, 44)",
      FlightPhase.Freefall      -> "rgb(214, 39, 40)",
      FlightPhase.UnderCanopy   -> "rgb(148, 103, 189)",
      FlightPhase.Landed        -> "rgb(140, 86, 75)",
      FlightPhase.BeforeTakeoff -> "rgb(128, 128, 128)",
    )

    val jsMarkers =
      transitions.map { t =>
        val color = colorMap.getOrElse(t.phase, "rgb(128, 128, 128)")
        s"""{
           |  x: [${t.index}],
           |  y: [${t.altitude}],
           |  name: '${t.phase}',
           |  type: 'scatter',
           |  mode: 'markers',
           |  marker: {color: '$color', size: 14, symbol: 'diamond'}
           |}""".stripMargin
      }

    s"var transitionMarkers = [${jsMarkers.mkString(",\n")}];"

  private def formatStage(point: Option[EventPoint]): String =
    point match {
      case Some(ep) => f"Point ${ep.index} at altitude ${ep.altitude}%.2fm"
      case None     => "Not detected"
    }

  final private case class PhaseTransition(
    phase: FlightPhase,
    index: Long,
    altitude: Double,
  )

  final private case class ChartState[A](
    indices: StringBuilder = StringBuilder(),
    altitudes: StringBuilder = StringBuilder(),
    velDs: StringBuilder = StringBuilder(),
    transitions: List[PhaseTransition] = Nil,
    prevPhase: FlightPhase = FlightPhase.BeforeTakeoff,
    lastStages: FlightEvents = FlightEvents.empty,
  )

}

private[beesight] object ChartGenerator {

  def apply(config: Config): ChartGenerator =
    new ChartGenerator(config)

}
