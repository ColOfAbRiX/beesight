package com.colofabrix.scala.beesight.output

import com.colofabrix.scala.beesight.*
import com.colofabrix.scala.beesight.IOConfig
import com.colofabrix.scala.beesight.config.Config
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.detection.readers.*
import com.colofabrix.scala.beesight.pipeline.Stage
import java.nio.file.{ Files, Path }
import java.time.{ Duration, Instant }

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
              _   <- IOConfig.blocking(Files.createDirectories(chartHtml.getParent))
              html = ChartHtml.build(state, metrics, swoop)
              _   <- IOConfig.blocking(Files.writeString(chartHtml, html))
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

/**
 * The data collected while streaming the points, enough to render the chart.
 */
final private[beesight] case class ChartState[A](
  times: Vector[Instant],
  altitudes: Vector[Double],
  velDs: Vector[Double],
  stages: FlightEvents,
)

private[beesight] object ChartState {

  def empty[A]: ChartState[A] =
    ChartState(Vector.empty, Vector.empty, Vector.empty, FlightEvents.empty)

}

/**
 * Renders the chart HTML: one figure with all the available traces, a legend to toggle them, and the detected points
 * marked on the plot.
 */
private[beesight] object ChartHtml {

  private lazy val echartsLib: String = {
    val stream = Option(getClass.getResourceAsStream("/chart/echarts.min.js"))
      .getOrElse(sys.error("chart/echarts.min.js is missing from the classpath"))
    try scala.io.Source.fromInputStream(stream, "UTF-8").mkString
    finally stream.close()
  }

  final private case class TraceSpec(
    name: String,
    values: Vector[Double],
    axis: Int,
    color: String,
    on: Boolean,
  )

  final private case class MarkerSpec(
    name: String,
    time: Instant,
    altitude: Double,
    symbol: String,
    color: String,
  )

  private val colors = Vector(
    "rgb(31, 119, 180)",
    "rgb(255, 127, 14)",
    "rgb(44, 160, 44)",
    "rgb(214, 39, 40)",
    "rgb(148, 103, 189)",
    "rgb(140, 86, 75)",
    "rgb(227, 119, 194)",
    "rgb(127, 127, 127)",
    "rgb(188, 189, 34)",
    "rgb(23, 190, 207)",
    "rgb(102, 51, 153)",
    "rgb(255, 215, 0)",
    "rgb(0, 128, 128)",
  )

  def build[A](
    state: ChartState[A],
    metrics: Option[Vector[FlightMetric]],
    swoop: Option[SwoopData],
  ): String =
    val t0        = state.times.headOption.getOrElse(Instant.EPOCH)
    val xs        = state.times.map(t => Duration.between(t0, t).toMillis / 1000.0)
    val traces    = traceSpecs(state, metrics)
    val markers   = markerSpecs(state.stages, swoop)
    val seriesJs = List(lineSeriesJs(traces, xs), markerSeriesJs(markers, t0)).filter(_.nonEmpty).mkString(",\n")
    val legend   = legendOptJs(traces)

    s"""<!DOCTYPE html>
       |<html>
       |<head>
       |  <meta charset="utf-8">
       |  <title>Flight Analysis</title>
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
       |      <li><strong>Takeoff:</strong> ${formatStage(state.stages.takeoff)}</li>
       |      <li><strong>Freefall:</strong> ${formatStage(state.stages.freefall)}</li>
       |      <li><strong>Canopy:</strong> ${formatStage(state.stages.canopy)}</li>
       |      <li><strong>Landing:</strong> ${formatStage(state.stages.landing)}</li>
       |    </ul>
       |${swoopInfoJs(swoop)}
       |  </div>
       |  <script>
       |$echartsLib
       |  </script>
       |  <script>
       |    var option = {
       |      animation: false,
       |      title: { text: 'Flight Data' },
       |      tooltip: { trigger: 'axis', axisPointer: { type: 'crosshair' } },
       |      legend: $legend,
       |      grid: { left: 60, right: 90, containLabel: true },
       |      xAxis: { type: 'value', name: 'Elapsed time (s)' },
       |      yAxis: [
       |        { type: 'value', name: 'Altitude (m)' },
       |        { type: 'value', name: 'Speed / angle / coefficient', position: 'right', offset: 40, scale: true }
       |      ],
       |      dataZoom: [{ type: 'inside' }],
       |      series: [
       |$seriesJs
       |      ]
       |    };
       |
       |    var chart = echarts.init(document.getElementById('plot'));
       |    chart.setOption(option);
       |    window.addEventListener('resize', function () { chart.resize(); });
       |  </script>
       |</body>
       |</html>
       |""".stripMargin

  private def traceSpecs[A](state: ChartState[A], metrics: Option[Vector[FlightMetric]]): List[TraceSpec] =
    metrics match
      case Some(m) =>
        val pick = (f: FlightMetric => Double) => m.map(f)
        List(
          TraceSpec("Altitude (m)", pick(_.altitude), 1, colors(0), on = true),
          TraceSpec("Vertical speed (m/s)", pick(_.verticalSpeed), 2, colors(1), on = true),
          TraceSpec("Horizontal speed (m/s)", pick(_.horizontalSpeed), 2, colors(2), on = true),
          TraceSpec("Total speed (m/s)", pick(_.totalSpeed), 2, colors(3), on = true),
          TraceSpec("Glide ratio", pick(_.glideRatio), 2, colors(4), on = false),
          TraceSpec("Lift coefficient", pick(_.liftCoefficient), 2, colors(5), on = false),
          TraceSpec("Drag coefficient", pick(_.dragCoefficient), 2, colors(6), on = false),
          TraceSpec("Dive angle (deg)", pick(_.diveAngle), 2, colors(7), on = false),
          TraceSpec("Dive rate (deg/s)", pick(_.diveRate), 2, colors(8), on = false),
          TraceSpec("Turn rate (deg/s)", pick(_.turnRate), 2, colors(9), on = false),
          TraceSpec("Acceleration H (m/s²)", pick(_.acceleration.horizontal), 2, colors(10), on = false),
          TraceSpec("Acceleration V (m/s²)", pick(_.acceleration.vertical), 2, colors(11), on = false),
          TraceSpec("Total energy (J/kg)", pick(_.totalEnergy), 2, colors(12), on = false),
        )
      case None =>
        List(
          TraceSpec("Altitude (m)", state.altitudes, 1, colors(0), on = true),
          TraceSpec("Vertical speed (m/s)", state.velDs, 2, colors(1), on = true),
        )

  private def markerSpecs(stages: FlightEvents, swoop: Option[SwoopData]): List[MarkerSpec] =
    val jump = List(
      stages.takeoff.map(p => MarkerSpec("Takeoff", p.time, p.altitude, "diamond", "rgb(44, 160, 44)")),
      stages.freefall.map(p => MarkerSpec("Exit", p.time, p.altitude, "diamond", "rgb(214, 39, 40)")),
      stages.canopy.map(p => MarkerSpec("Canopy", p.time, p.altitude, "diamond", "rgb(148, 103, 189)")),
      stages.landing.map(p => MarkerSpec("Landing", p.time, p.altitude, "diamond", "rgb(140, 86, 75)")),
    ).flatten

    val swoopMarkers = swoop.toList.flatMap { data =>
      val msl = (p: EventPoint) => p.altitude + data.ground.value
      List(
        MarkerSpec("Pattern entry", data.patternEntry.time, msl(data.patternEntry), "star", "rgb(31, 119, 180)"),
        MarkerSpec("Dive point", data.divePoint.time, msl(data.divePoint), "star", "rgb(255, 127, 14)"),
        MarkerSpec(
          "Turn initiation",
          data.turnInitiation.time,
          msl(data.turnInitiation),
          "star",
          "rgb(214, 39, 40)",
        ),
        MarkerSpec("Rollout start", data.rolloutStart.time, msl(data.rolloutStart), "star", "rgb(44, 160, 44)"),
        MarkerSpec("Bottom of turn", data.bottomOfTurn.time, msl(data.bottomOfTurn), "star", "rgb(148, 103, 189)"),
        MarkerSpec("Stop", data.stop.time, msl(data.stop), "star", "rgb(140, 86, 75)"),
      )
    }

    jump ++ swoopMarkers

  private def lineSeriesJs(traces: List[TraceSpec], xs: Vector[Double]): String =
    traces
      .map { t =>
        val data = xs.zip(t.values).map((x, y) => s"[$x, $y]").mkString("[", ", ", "]")
        s"""{
           |  name: '${t.name}',
           |  type: 'line',
           |  showSymbol: false,
           |  yAxisIndex: ${t.axis - 1},
           |  lineStyle: { color: '${t.color}', width: 1.5 },
           |  data: $data
           |}""".stripMargin
      }
      .mkString(",\n")

  private def markerSeriesJs(markers: List[MarkerSpec], t0: Instant): String =
    if markers.isEmpty then ""
    else
      val points = markers
        .map { m =>
          val x = Duration.between(t0, m.time).toMillis / 1000.0
          s"{ name: '${m.name}', value: [$x, ${m.altitude}], symbol: '${m.symbol}', itemStyle: { color: '${m.color}' } }"
        }
        .mkString("[", ", ", "]")

      s"""{
         |  name: 'Detected points',
         |  type: 'scatter',
         |  symbolSize: 12,
         |  label: { show: true, formatter: '{b}', position: 'top' },
         |  data: $points
         |}""".stripMargin

  private def legendOptJs(traces: List[TraceSpec]): String =
    val names    = traces.map(t => s"'${t.name}'").mkString("[", ", ", "]")
    val selected = traces.map(t => s"'${t.name}': ${t.on}").mkString("{", ", ", "}")
    s"{ data: $names, selected: $selected }"

  private def swoopInfoJs(swoop: Option[SwoopData]): String =
    swoop.fold("") { data =>
      s"""    <h3>Swoop Analysis</h3>
         |    <ul>
         |      <li><strong>Pattern entry:</strong> ${formatPoint(data.patternEntry)}</li>
         |      <li><strong>Dive point:</strong> ${formatPoint(data.divePoint)}</li>
         |      <li><strong>Turn initiation:</strong> ${formatPoint(data.turnInitiation)}</li>
         |      <li><strong>Rollout start:</strong> ${formatPoint(data.rolloutStart)}</li>
         |      <li><strong>Bottom of turn:</strong> ${formatPoint(data.bottomOfTurn)}</li>
         |      <li><strong>Stop:</strong> ${formatPoint(data.stop)}</li>
         |      <li><strong>Degrees of rotation:</strong> ${data.degreesOfRotation.value}° ${data.turnDirection.productPrefix}</li>
         |    </ul>"""
    }

  private def formatStage(point: Option[EventPoint]): String =
    point.fold("Not detected")(formatPoint)

  private def formatPoint(point: EventPoint): String =
    f"Point ${point.index} at altitude ${point.altitude}%.2fm"

}
