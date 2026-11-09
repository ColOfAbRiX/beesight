package com.colofabrix.scala.beesight.output

import com.colofabrix.scala.beesight.*
import com.colofabrix.scala.beesight.IOConfig
import com.colofabrix.scala.beesight.config.Config
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.detection.readers.*
import com.colofabrix.scala.beesight.pipeline.Stage
import io.circe.Json
import io.circe.syntax.*
import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }
import java.math.MathContext
import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path }
import java.time.{ Duration, Instant }
import java.util.Base64
import java.util.zip.{ GZIPInputStream, GZIPOutputStream }

// FIXME: Split this file into multiuple files based on responsibility and create a new package for chart generation

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
    unit: String,
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
    fileName: String,
  ): String =
    s"""<!DOCTYPE html>
       |<html>
       |<head>
       |  <meta charset="utf-8">
       |  <title>Flight Analysis - ${fileName}</title>
       |  <style>
       |    body { margin: 0; padding: 10px; font-family: sans-serif; background: #f5f5f5; }
       |    h1 { color: #333; font-size: 20px; line-height: 24px; margin: 0 0 6px; }
       |    #toolbar { display: flex; align-items: center; gap: 16px; margin-bottom: 8px; }
       |    #toolbar label { font-size: 13px; color: #333; display: flex; align-items: center; gap: 4px; }
       |    .pill { display: inline-block; padding: 4px 12px; border: none; border-radius: 12px; color: white; font-size: 13px; cursor: pointer; font-family: inherit; }
       |    .mode-z { background: #93b7d4; }
       |    .mode-z.active { background: #1f77b4; }
       |    .mode-m { background: #f5c08a; }
       |    .mode-m.active { background: #ff7f0e; }
       |    .set { background: #7f8c8d; }
       |    .set.armed { background: #c0392b; }
       |    #chart { width: 100%; background: white; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
       |    .info table { border-collapse: collapse; margin-top: 8px; }
       |    .info th, .info td { border: 1px solid #e3e3e3; padding: 6px 12px; font-size: 13px; text-align: right; color: #333; }
       |    .info th { background: #f7f7f7; font-weight: 600; }
       |    .info th:first-child, .info td:first-child { text-align: left; }
       |    .info { margin-top: 12px; padding: 12px; background: white; border-radius: 8px; }
       |    .info h3 { margin: 0 0 4px; font-size: 15px; color: #333; }
       |  </style>
       |</head>
       |<body>
       |  <h1>Flight Analysis - ${fileName}</h1>
       |  <div id="toolbar">
       |    <button id="pill-zoom" class="pill mode-z">Zoom (Z)</button>
       |    <button id="pill-measure" class="pill mode-m">Measure (M)</button>
       |    <button id="pill-exit" class="pill set">Set Exit time (X)</button>
       |    <button id="pill-ground" class="pill set">Set Ground (N)</button>
       |    <label><input type="checkbox" id="tooltip-toggle" checked> Tooltip</label>
       |    <label><input type="checkbox" id="points-toggle"> Points</label>
       |  </div>
       |  <div id="chart"></div>
       |  <div class="info">
       |${infoTableJs("Detected Flight Stages", stageRows(state.stages), "")}
       |${swoopTableJs(swoop)}
       |  </div>
       |  <script>__ECHARTS__</script>
       |  <script>__CHART_INIT__</script>
       |  <script>
       |    var chartDefaults = ${roundNumbers(chartDefaultsJson(state, metrics)).noSpaces};
       |    var optionGz = "${gzipBase64(roundNumbers(option(state, metrics, swoop)).noSpaces)}";
       |    (async function () {
       |      try {
       |        var bytes = Uint8Array.from(atob(optionGz), function (c) { return c.charCodeAt(0); });
       |        var stream = new Blob([bytes]).stream().pipeThrough(new DecompressionStream('gzip'));
       |        var option = JSON.parse(await new Response(stream).text());
       |        initChart(option);
       |      } catch (error) {
       |        document.getElementById('chart').textContent = 'Failed to load chart data: ' + error;
       |      }
       |    })();
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
    val xMax    = xs.lastOption.map(last => last + math.max(last * 0.02, 0.1)).getOrElse(1.0)

    Json.obj(
      "animation" -> Json.False,
      "tooltip" -> Json.obj(
        "trigger" -> "axis".asJson,
        "axisPointer" -> Json.obj("type" -> "cross".asJson, "label" -> Json.obj("show" -> Json.False)),
        "extraCssText" -> "font-size: 11px; padding: 4px 8px;".asJson,
      ),
      "legend"    -> legendJson(traces),
      "grid"      -> Json.obj(
        "left" -> (traces.count(_.on) * 20).asJson,
        "right" -> 30.asJson,
        "top" -> 64.asJson,
        "bottom" -> 22.asJson,
        "containLabel" -> Json.False,
      ),
      "toolbox"   -> Json.obj("show" -> Json.False),
      "xAxis" -> Json.arr(
        Json.obj(
          "type" -> "value".asJson,
          "name" -> "Elapsed time (s)".asJson,
          "min" -> 0.0.asJson,
          "max" -> xMax.asJson,
          "axisLabel" -> Json.obj("color" -> "#555".asJson),
          "axisTick" -> Json.obj("show" -> Json.False),
          "axisPointer" -> Json.obj("label" -> Json.obj("show" -> Json.False)),
        ),
        Json.obj(
          "type" -> "value".asJson,
          "position" -> "top".asJson,
          "min" -> 0.0.asJson,
          "max" -> xMax.asJson,
          "axisLabel" -> Json.obj("color" -> "#555".asJson),
          "axisTick" -> Json.obj("show" -> Json.False),
          "axisPointer" -> Json.obj("label" -> Json.obj("show" -> Json.False)),
        ),
      ),
      "yAxis" -> Json.arr(
        traces.map { trace =>
          val bound = axisBounds(trace.values)
          Json.obj(
            "type" -> "value".asJson,
            "name" -> trace.unit.asJson,
            "nameLocation" -> "start".asJson,
            "position" -> "left".asJson,
            "offset" -> 0.asJson,
            "show" -> trace.on.asJson,
            "min" -> bound._1.asJson,
            "max" -> bound._2.asJson,
            "axisLine" -> Json.obj("lineStyle" -> Json.obj("color" -> trace.color.asJson)),
            "axisLabel" -> Json.obj("color" -> trace.color.asJson),
            "axisTick" -> Json.obj("show" -> Json.False),
            "axisPointer" -> Json.obj("label" -> Json.obj("show" -> Json.False)),
            "nameTextStyle" -> Json.obj("color" -> trace.color.asJson, "fontSize" -> 10.asJson),
            "splitLine" -> Json.obj("show" -> Json.False),
          )
        }*
      ),
      "dataZoom" -> Json.arr(
        Json.obj(
          "type" -> "inside".asJson,
          "xAxisIndex" -> Json.arr(0.asJson, 1.asJson),
          "moveOnMouseMove" -> Json.False,
          "moveOnMouseWheel" -> "shift".asJson,
        ),
      ),
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

  private[beesight] def roundNumbers(json: Json): Json =
    json.asNumber match
      case Some(_) =>
        val value = json.asNumber.map(_.toDouble).getOrElse(0.0)
        Json.fromDoubleOrNull(BigDecimal(value, new MathContext(6)).toDouble)
      case None =>
        json.asArray
          .map(arr => Json.fromValues(arr.map(roundNumbers)))
          .orElse(json.asObject.map(obj => Json.fromFields(obj.toList.map { case (key, value) => key -> roundNumbers(value) })))
          .getOrElse(json)

  private def gzipBase64(text: String): String =
    val buffer  = new ByteArrayOutputStream()
    val gzip    = new GZIPOutputStream(buffer)
    gzip.write(text.getBytes(StandardCharsets.UTF_8))
    gzip.close()
    Base64.getEncoder.encodeToString(buffer.toByteArray)

  private[beesight] def gunzipBase64(encoded: String): String =
    val gzip = new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder.decode(encoded)))
    val out  = Iterator.continually(gzip.read).takeWhile(_ != -1).map(_.toByte).toArray
    new String(out, StandardCharsets.UTF_8)

  private def chartDefaultsJson[A](state: ChartState[A], metrics: Option[Vector[FlightMetric]]): Json =
    val t0      = state.times.headOption.getOrElse(Instant.EPOCH)
    val xs      = state.times.map(t => Duration.between(t0, t).toMillis / 1000.0)
    val traced  = traceSpecs(state, metrics).find(_.unit == "m")
    val exitX   = state.stages.freefall.map(p => Duration.between(t0, p.time).toMillis / 1000.0).getOrElse(0.0)
    val landing = state.stages.landing.map(p => Duration.between(t0, p.time).toMillis / 1000.0)
    val groundY = landing
      .flatMap(lx => traced.map(_.values).flatMap(vals => xs.zip(vals).collectFirst { case (`lx`, y) => y }))
      .getOrElse(0.0)
    Json.obj("exit" -> exitX.asJson, "ground" -> groundY.asJson)

  private def axisBounds(values: Seq[Double]): (Double, Double) =
    if values.isEmpty then (0.0, 1.0)
    else
      val min = values.min
      val max = values.max
      val pad = math.max((max - min) * 0.02, 0.1)
      (min - pad, max + pad)

  private def traceSpecs[A](state: ChartState[A], metrics: Option[Vector[FlightMetric]]): List[TraceSpec] =
    metrics match
      case Some(m) =>
        val pick = (f: FlightMetric => Double) => m.map(f)
        List(
          TraceSpec("Altitude (m)", pick(_.altitude), "m", colors(0), on = true),
          TraceSpec("Vertical speed (m/s)", pick(_.verticalSpeed), "m/s", colors(1), on = true),
          TraceSpec("Horizontal speed (m/s)", pick(_.horizontalSpeed), "m/s", colors(2), on = false),
          TraceSpec("Total speed (m/s)", pick(_.totalSpeed), "m/s", colors(3), on = false),
          TraceSpec("Glide ratio", pick(_.glideRatio), "ratio / coeff", colors(4), on = false),
          TraceSpec("Lift coefficient", pick(_.liftCoefficient), "ratio / coeff", colors(5), on = false),
          TraceSpec("Drag coefficient", pick(_.dragCoefficient), "ratio / coeff", colors(6), on = false),
          TraceSpec("Dive angle (deg)", pick(_.diveAngle), "deg", colors(7), on = false),
          TraceSpec("Dive rate (deg/s)", pick(_.diveRate), "deg/s", colors(8), on = false),
          TraceSpec("Turn rate (deg/s)", pick(_.turnRate), "deg/s", colors(9), on = false),
          TraceSpec("Acceleration H (m/s²)", pick(_.acceleration.horizontal), "m/s²", colors(10), on = false),
          TraceSpec("Acceleration V (m/s²)", pick(_.acceleration.vertical), "m/s²", colors(11), on = false),
          TraceSpec("Total energy (J/kg)", pick(_.totalEnergy), "J/kg", colors(12), on = false),
        )
      case None =>
        List(
          TraceSpec("Altitude (m)", state.altitudes, "m", colors(0), on = true),
          TraceSpec("Vertical speed (m/s)", state.velDs, "m/s", colors(1), on = true),
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
    traces.zipWithIndex.map { case (t, index) =>
      val data =
        if t.unit == "m" then altitudeDataJson(t, markers, xs, t0)
        else Json.fromValues(xs.zip(t.values).map((x, y) => Json.arr(x.asJson, y.asJson)))

      Json.obj(
        "name"       -> t.name.asJson,
        "type"       -> "line".asJson,
        "symbol"     -> "none".asJson,
        "yAxisIndex" -> index.asJson,
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
        "label"      -> Json.obj("show" -> Json.True, "formatter" -> "{b}".asJson, "position" -> "bottom".asJson),
      )
    }

    Json.fromValues((lines ++ marks).sortBy(_._1).map(_._2))

  private def stageRows(stages: FlightEvents): List[(String, Option[EventPoint])] = List(
    "Takeoff" -> stages.takeoff,
    "Freefall" -> stages.freefall,
    "Canopy" -> stages.canopy,
    "Landing" -> stages.landing,
  )

  private def infoTableJs(caption: String, rows: List[(String, Option[EventPoint])], extraRows: String): String =
    val body = rows.map {
      case (event, Some(point)) =>
        s"""      <tr><td>$event</td><td>${point.index}</td><td>${formatAltitude(point.altitude)}</td></tr>"""
      case (event, None) =>
        s"""      <tr><td>$event</td><td colspan="2">Not detected</td></tr>"""
    }.mkString("\n")

    val extras = if extraRows.isEmpty then "" else "\n" + extraRows

    s"""    <h3>$caption</h3>
       |    <table>
       |      <thead><tr><th>Event</th><th>Point</th><th>Altitude (m)</th></tr></thead>
       |      <tbody>
       |$body$extras
       |      </tbody>
       |    </table>""".stripMargin

  private def swoopTableJs(swoop: Option[SwoopData]): String =
    swoop.fold("") { data =>
      val rows = List[(String, Option[EventPoint])](
        "Pattern entry" -> Some(data.patternEntry),
        "Dive point" -> Some(data.divePoint),
        "Turn initiation" -> Some(data.turnInitiation),
        "Rollout start" -> Some(data.rolloutStart),
        "Bottom of turn" -> Some(data.bottomOfTurn),
        "Stop" -> Some(data.stop),
      )
      val rotation =
        s"""      <tr><td>Degrees of rotation</td><td colspan="2">${formatAltitude(data.degreesOfRotation.value)}° ${data.turnDirection.productPrefix}</td></tr>"""

      infoTableJs("Swoop Analysis", rows, rotation)
    }

  private def formatAltitude(value: Double): String =
    "%,.2f".formatLocal(java.util.Locale.UK, value)

}
