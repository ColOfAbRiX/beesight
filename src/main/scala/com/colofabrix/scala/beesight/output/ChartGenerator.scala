package com.colofabrix.scala.beesight.output

import com.colofabrix.scala.beesight.*
import com.colofabrix.scala.beesight.IOConfig
import com.colofabrix.scala.beesight.config.Config
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.detection.readers.*
import com.colofabrix.scala.beesight.pipeline.Stage
import io.circe.Json
import io.circe.syntax.*
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

  private def resource(name: String): String = {
    val stream = Option(getClass.getResourceAsStream(name))
      .getOrElse(sys.error(s"$name is missing from the classpath"))
    try scala.io.Source.fromInputStream(stream, "UTF-8").mkString
    finally stream.close()
  }

  private lazy val echartsLib   = resource("/chart/echarts.min.js")
  private lazy val chartInitJs = resource("/chart/chart-init.js")

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
    s"""<!DOCTYPE html>
       |<html>
       |<head>
       |  <meta charset="utf-8">
       |  <title>Flight Analysis</title>
       |  <style>
       |    body { margin: 0; padding: 20px; font-family: sans-serif; background: #f5f5f5; }
       |    h1 { color: #333; }
       |    #chart { width: 100%; height: 80vh; background: white; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
       |    #badge { display: inline-block; padding: 4px 12px; margin-bottom: 10px; border-radius: 12px; background: #333; color: white; font-size: 13px; }
       |    #stats table { border-collapse: collapse; margin-top: 12px; }
       |    #stats th, #stats td { border: 1px solid #ddd; padding: 4px 10px; font-size: 13px; text-align: right; }
       |    #stats th:first-child, #stats td:first-child { text-align: left; }
       |    #stats p { margin: 8px 0 0; color: #555; font-size: 13px; }
       |    .info { margin-top: 20px; padding: 15px; background: white; border-radius: 8px; }
       |    .info h3 { margin-top: 0; }
       |  </style>
       |</head>
       |<body>
       |  <h1>Flight Analysis</h1>
       |  <div id="badge"></div>
       |  <div id="chart"></div>
       |  <div id="stats"></div>
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
       |  <script>__ECHARTS__</script>
       |  <script>__CHART_INIT__</script>
       |  <script>
       |    var option = ${option(state, metrics, swoop).noSpaces};
       |    initChart(option);
       |  </script>
       |</body>
       |</html>
       |""".stripMargin.replace("__ECHARTS__", echartsLib).replace("__CHART_INIT__", chartInitJs)

  def option[A](
    state: ChartState[A],
    metrics: Option[Vector[FlightMetric]],
    swoop: Option[SwoopData],
  ): Json =
    val t0      = state.times.headOption.getOrElse(Instant.EPOCH)
    val xs      = state.times.map(t => Duration.between(t0, t).toMillis / 1000.0)
    val traces  = traceSpecs(state, metrics)
    val markers = markerSpecs(state.stages, swoop)

    Json.obj(
      "animation" -> Json.False,
      "title"     -> Json.obj("text" -> "Flight Data".asJson),
      "tooltip"   -> Json.obj("trigger" -> "axis".asJson, "axisPointer" -> Json.obj("type" -> "crosshair".asJson)),
      "legend"    -> legendJson(traces),
      "grid"      -> Json.obj("left" -> 60.asJson, "right" -> 90.asJson, "containLabel" -> Json.True),
      "xAxis"     -> Json.obj("type" -> "value".asJson, "name" -> "Elapsed time (s)".asJson),
      "yAxis"     -> Json.arr(
        Json.obj("type" -> "value".asJson, "name" -> "Altitude (m)".asJson),
        Json.obj(
          "type"     -> "value".asJson,
          "name"     -> "Speed / angle / coefficient".asJson,
          "position" -> "right".asJson,
          "offset"   -> 40.asJson,
          "scale"    -> Json.True,
        ),
      ),
      "dataZoom" -> Json.arr(Json.obj("type" -> "inside".asJson)),
      "brush" -> Json.obj(
        "xAxisIndex"    -> Json.arr(0.asJson),
        "brushMode"     -> "single".asJson,
        "transformable" -> Json.False,
        "brushStyle"    -> Json.obj(
          "color"       -> "rgba(85, 85, 85, 0.08)".asJson,
          "borderColor" -> "#555".asJson,
          "borderWidth" -> 1.asJson,
        ),
      ),
      "series"   -> Json.fromValues(seriesJson(traces, markers, xs, t0)),
    )

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
        MarkerSpec("Pattern entry", data.patternEntry.time, msl(data.patternEntry), "pin", "rgb(31, 119, 180)"),
        MarkerSpec("Dive point", data.divePoint.time, msl(data.divePoint), "pin", "rgb(255, 127, 14)"),
        MarkerSpec(
          "Turn initiation",
          data.turnInitiation.time,
          msl(data.turnInitiation),
          "pin",
          "rgb(214, 39, 40)",
        ),
        MarkerSpec("Rollout start", data.rolloutStart.time, msl(data.rolloutStart), "pin", "rgb(44, 160, 44)"),
        MarkerSpec("Bottom of turn", data.bottomOfTurn.time, msl(data.bottomOfTurn), "pin", "rgb(148, 103, 189)"),
        MarkerSpec("Stop", data.stop.time, msl(data.stop), "pin", "rgb(140, 86, 75)"),
      )
    }

    jump ++ swoopMarkers

  private def legendJson(traces: List[TraceSpec]): Json =
    Json.obj(
      "data"     -> Json.fromValues(traces.map(_.name.asJson)),
      "selected" -> Json.fromFields(traces.map(t => t.name -> t.on.asJson)),
    )

  private def seriesJson(
    traces: List[TraceSpec],
    markers: List[MarkerSpec],
    xs: Vector[Double],
    t0: Instant,
  ): List[Json] =
    traces.map { t =>
      val data =
        if t.axis == 1 then altitudeDataJson(t, markers, xs, t0)
        else Json.fromValues(xs.zip(t.values).map((x, y) => Json.arr(x.asJson, y.asJson)))

      Json.obj(
        "name"       -> t.name.asJson,
        "type"       -> "line".asJson,
        "symbol"     -> "none".asJson,
        "yAxisIndex" -> (t.axis - 1).asJson,
        "lineStyle"  -> Json.obj("color" -> t.color.asJson, "width" -> 1.5.asJson),
        "data"       -> data,
      )
    }

  private def altitudeDataJson(
    trace: TraceSpec,
    markers: List[MarkerSpec],
    xs: Vector[Double],
    t0: Instant,
  ): Json =
    val pairs = xs.zip(trace.values)
    val lines = pairs.map((x, y) => x -> Json.arr(x.asJson, y.asJson))
    val marks = markers.map { m =>
      val x = Duration.between(t0, m.time).toMillis / 1000.0
      val y = pairs.collectFirst { case (`x`, altitude) => altitude }.getOrElse(m.altitude)
      x -> Json.obj(
        "name"       -> m.name.asJson,
        "value"      -> Json.arr(x.asJson, y.asJson),
        "symbol"     -> m.symbol.asJson,
        "symbolSize" -> 12.asJson,
        "itemStyle"  -> Json.obj("color" -> m.color.asJson),
        "label"      -> Json.obj("show" -> Json.True, "formatter" -> "{b}".asJson, "position" -> "top".asJson),
      )
    }

    Json.fromValues((lines ++ marks).sortBy(_._1).map(_._2))

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
