package com.colofabrix.scala.beesight.debug

import cats.effect.IO
import com.colofabrix.scala.beesight.model.*
import java.nio.file.*

/**
 * Generates interactive HTML charts showing flight data and detected stages
 */
object ChartGenerator {

  /**
   * Generates an interactive HTML chart for a flight
   */
  def generate(data: fs2.Stream[IO, FlysightPoint], stages: FlightStagesPoints, outputPath: Path): IO[Unit] =
    for
      allPoints <- data.compile.toVector
      html       = createHtmlChart(allPoints, stages)
      _         <- writeHtmlFile(html, outputPath)
    yield ()

  private def writeHtmlFile(html: String, outputPath: Path): IO[Unit] =
    IO.blocking {
      Files.createDirectories(Option(outputPath.getParent).getOrElse(Paths.get(".")))
      val _ = Files.writeString(outputPath, html)
    }

  private def createHtmlChart(data: Vector[FlysightPoint], stages: FlightStagesPoints): String =
    val (indices, altitudes, velDs) =
      data
        .zipWithIndex
        .foldLeft((StringBuilder(), StringBuilder(), StringBuilder())) {
          case ((idxAcc, altAcc, velAcc), (point, idx)) =>
            val sep       = if idx == 0 then "" else ","
            val indices   = idxAcc.append(sep).append(idx)
            val altitudes = altAcc.append(sep).append(point.hMSL)
            val velDs     = velAcc.append(sep).append(point.velD)
            (indices, altitudes, velDs)
        }

    val indicesJs   = s"[${indices.result()}]"
    val altitudesJs = s"[${altitudes.result()}]"
    val velDsJs     = s"[${velDs.result()}]"

    val stageMarkers = createStageMarkersJs(stages, data)

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
       |    <p><strong>Valid Jump:</strong> ${if stages.isValid then "Yes" else "No"}</p>
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
       |    $stageMarkers
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
       |    Plotly.newPlot('plot', [altitudeTrace, velocityTrace].concat(stageMarkers), layout, config);
       |  </script>
       |</body>
       |</html>
       |""".stripMargin

  private def createStageMarkersJs(stages: FlightStagesPoints, data: Vector[FlysightPoint]): String =
    val markers =
      Vector(
        ("Takeoff", stages.takeoff, "rgb(44, 160, 44)"),
        ("Freefall", stages.freefall, "rgb(214, 39, 40)"),
        ("Canopy", stages.canopy, "rgb(148, 103, 189)"),
        ("Landing", stages.landing, "rgb(140, 86, 75)"),
      )

    val jsMarkers = markers.flatMap {
      case (name, Some(DataPoint(idx, altOpt)), color) =>
        val altitude = altOpt.getOrElse(data.lift(idx.toInt).map(_.hMSL).getOrElse(0.0))
        Some(
          s"""{
             |  x: [$idx],
             |  y: [$altitude],
             |  name: '$name',
             |  type: 'scatter',
             |  mode: 'markers',
             |  marker: {color: '$color', size: 14, symbol: 'diamond'}
             |}""".stripMargin,
        )
      case _ =>
        None
    }

    s"var stageMarkers = [${jsMarkers.mkString(",\n")}];"

  private def formatStage(point: Option[DataPoint]): String =
    point match {
      case Some(DataPoint(idx, Some(alt))) => f"Point $idx at altitude $alt%.2fm"
      case Some(DataPoint(idx, None))      => s"Point $idx (altitude unknown)"
      case None                            => "Not detected"
    }

}
