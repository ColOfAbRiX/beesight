package com.colofabrix.scala.beesight.output.chart

import com.colofabrix.scala.beesight.detection.config.{ Altitude, Angle, Speed }
import com.colofabrix.scala.beesight.detection.model.*
import io.circe.Json
import io.circe.syntax.*
import java.time.{ Duration, Instant }
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class ChartGeneratorSpec extends AnyFreeSpec with Matchers {

  private val at     = Instant.parse("2026-01-15T10:00:00Z")
  private val point  = EventPoint(0, 1200.0, at, GeoVector(0.0, 0.0, 40.0))
  private val events = FlightEvents(
    takeoff = Some(point.copy(index = 0)),
    freefall = Some(point.copy(index = 1, time = at.plusSeconds(1))),
    canopy = Some(point.copy(index = 2, time = at.plusSeconds(2))),
    landing = Some(point.copy(index = 2, time = at.plusSeconds(2))),
  )
  private val state = ChartState(
    times = Vector(at, at.plusSeconds(1), at.plusSeconds(2)),
    altitudes = Vector(1200.0, 1100.0, 1000.0),
    velDs = Vector(40.0, 42.0, 44.0),
    stages = events,
  )
  private val metrics = Vector(metricAt(0), metricAt(1), metricAt(2))
  private val swoop   = SwoopData(
    turnInitiation = point.copy(index = 1, time = at.plusSeconds(1)),
    rolloutStart = point.copy(index = 2, time = at.plusSeconds(2)),
    maxVerticalSpeed = SpeedAtAltitude(3, Speed(60.0), Altitude(1800.0)),
    maxTotalSpeed = SpeedAtAltitude(4, Speed(75.0), Altitude(1500.0)),
    maxHorizontalSpeed = SpeedAtAltitude(5, Speed(30.0), Altitude(900.0)),
    degreesOfRotation = Angle(315.0),
    turnDirection = TurnDirection.Right,
    timeToExecuteTurn = Duration.ofMillis(3750),
    ground = Altitude(100.0),
    patternEntry = point.copy(index = 1, time = at.plusSeconds(1)),
    divePoint = point.copy(index = 2, time = at.plusSeconds(2)),
    stop = point.copy(index = 2, time = at.plusSeconds(2)),
    bottomOfTurn = point.copy(index = 2, time = at.plusSeconds(2)),
  )

  private val cutState = ChartState(
    times = Vector(at.plusSeconds(100), at.plusSeconds(101), at.plusSeconds(102)),
    altitudes = Vector(1000.0, 990.0, 980.0),
    velDs = Vector(40.0, 42.0, 44.0),
    stages = FlightEvents(
      takeoff = Some(point.copy(index = 1000, time = at.plusSeconds(100))),
      freefall = Some(point.copy(index = 4072, time = at.plusSeconds(101))),
      canopy = Some(point.copy(index = 4103, time = at.plusSeconds(101))),
      landing = Some(point.copy(index = 5148, time = at.plusSeconds(102))),
    ),
  )
  private val cutSwoop = swoop.copy(
    patternEntry = point.copy(index = 4000, time = at.plusSeconds(101)),
    divePoint = point.copy(index = 4500, time = at.plusSeconds(102)),
  )

  private val palette = List(
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

  private val metricNames: List[(String, Int)] = List(
    ("Altitude (m)", 0),
    ("Vertical speed (m/s)", 1),
    ("Horizontal speed (m/s)", 2),
    ("Total speed (m/s)", 3),
    ("Glide ratio", 4),
    ("Lift coefficient", 5),
    ("Drag coefficient", 6),
    ("Dive angle (deg)", 7),
    ("Dive rate (deg/s)", 8),
    ("Turn rate (deg/s)", 9),
    ("Acceleration H (m/s²)", 10),
    ("Acceleration V (m/s²)", 11),
    ("Total energy (J/kg)", 12),
  )

  private val basicNames: List[(String, Int)] = List(
    ("Altitude (m)", 0),
    ("Vertical speed (m/s)", 1),
  )

  "ChartOption option" - {

    "renders every metric trace as a line series" in {
      val expected = configsOf(metricNames)

      val option = ChartOption.option(state, Some(metrics), None)
      val actual = seriesOf(option).map(configOf)

      actual shouldBe expected
    }

    "renders only the basic traces when metrics are missing" in {
      val expected = configsOf(basicNames)

      val option = ChartOption.option(state, None, None)
      val actual = seriesOf(option).map(configOf)

      actual shouldBe expected
    }

    "selects only the minimal set of traces on load" in {
      val expectedFull  = legendOf(metricNames.map(_._1), metricNames.take(2).map(_._1))
      val expectedBasic = legendOf(basicNames.map(_._1), basicNames.map(_._1))

      val full  = field(ChartOption.option(state, Some(metrics), None), "legend")
      val basic = field(ChartOption.option(state, None, None), "legend")

      full shouldBe expectedFull
      basic shouldBe expectedBasic
    }

    "keeps the core option shape" in {
      val xMax = 2.0 + math.max(2.0 * 0.02, 0.1)
      val acc  = metricAt(0).acceleration
      val traceBounds = List(
        boundsOf(Vector(1200.5, 1199.5, 1198.5)),
        boundsOf(Vector.fill(3)(40.0)),
        boundsOf(Vector.fill(3)(30.0)),
        boundsOf(Vector.fill(3)(50.0)),
        boundsOf(Vector.fill(3)(0.75)),
        boundsOf(Vector.fill(3)(0.4)),
        boundsOf(Vector.fill(3)(0.1)),
        boundsOf(Vector(45.0)),
        boundsOf(Vector(-3.5)),
        boundsOf(Vector(90.0)),
        boundsOf(Vector(acc.horizontal)),
        boundsOf(Vector(acc.vertical)),
        boundsOf(Vector(2500.0)),
      )
      val traceUnits = List(
        "m",
        "m/s",
        "m/s",
        "m/s",
        "ratio / coeff",
        "ratio / coeff",
        "ratio / coeff",
        "deg",
        "deg/s",
        "deg/s",
        "m/s²",
        "m/s²",
        "J/kg",
      )
      val traceOn = List(true, true) ++ List.fill(11)(false)
      val expected = Json.obj(
        "animation" -> Json.False,
        "tooltip" -> Json.obj(
          "trigger" -> "axis".asJson,
          "axisPointer" -> Json.obj("type" -> "cross".asJson, "label" -> Json.obj("show" -> Json.False)),
          "extraCssText" -> "font-size: 11px; padding: 4px 8px;".asJson,
        ),
        "grid" -> Json.obj("left" -> 120.asJson, "right" -> 30.asJson, "top" -> 64.asJson, "bottom" -> 36.asJson, "containLabel" -> Json.False),
        "toolbox" -> Json.obj("show" -> Json.False),
        "xAxis" -> Json.arr(
          Json.obj(
            "type" -> "value".asJson,
            "min" -> 0.0.asJson,
            "max" -> xMax.asJson,
            "axisLabel" -> Json.obj("show" -> Json.False),
            "axisTick" -> Json.obj("show" -> Json.False),
            "axisPointer" -> Json.obj("label" -> Json.obj("show" -> Json.False)),
          ),
          Json.obj(
            "type" -> "value".asJson,
            "position" -> "bottom".asJson,
            "min" -> 0.0.asJson,
            "max" -> xMax.asJson,
            "axisLabel" -> Json.obj("color" -> "#555".asJson, "showMinLabel" -> Json.False),
            "axisTick" -> Json.obj("show" -> Json.False),
            "axisPointer" -> Json.obj("label" -> Json.obj("show" -> Json.False)),
          ),
        ),
        "yAxis" -> Json.arr(
          traceUnits
            .zip(traceBounds)
            .zip(traceOn)
            .zip(palette)
            .map { case (((unit, bound), on), color) => traceAxis(unit, bound, on, color) }*
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
          "xAxisIndex" -> Json.arr(0.asJson),
          "brushMode" -> "single".asJson,
          "transformable" -> Json.False,
          "brushStyle" -> Json.obj(
            "color" -> "rgba(85, 85, 85, 0.08)".asJson,
            "borderColor" -> "#555".asJson,
            "borderWidth" -> 1.asJson,
          ),
        ),
      )

      val option = ChartOption.option(state, Some(metrics), None)
      val actual = option.asObject.map(_.remove("series").remove("legend").asJson).getOrElse(Json.Null)

      actual shouldBe expected
    }

    "keeps the speed data as coordinate pairs" in {
      val expected = List(List(0.0, 40.0), List(1.0, 40.0), List(2.0, 40.0))

      val option = ChartOption.option(state, Some(metrics), None)
      val actual = seriesOf(option)(1).hcursor.downField("data").as[List[List[Double]]].getOrElse(Nil)

      actual shouldBe expected
    }

    "marks the detected jump events on the altitude profile" in {
      val expected = List(
        ("Takeoff", 0.0, 1200.5),
        ("Exit", 1.0, 1199.5),
        ("Canopy", 2.0, 1198.5),
        ("Landing", 2.0, 1198.5),
      )

      val option = ChartOption.option(state, Some(metrics), None)
      val actual = markersOf(option)

      actual shouldBe expected
      option.noSpaces should include("\"position\":\"bottom\"")
    }

    "marks the swoop points when the swoop analysis is available" in {
      val expected = List(
        ("Takeoff", 0.0, 1200.5),
        ("Exit", 1.0, 1199.5),
        ("Turn initiation", 1.0, 1199.5),
        ("Canopy", 2.0, 1198.5),
        ("Landing", 2.0, 1198.5),
        ("Dive point", 2.0, 1198.5),
        ("Rollout start", 2.0, 1198.5),
        ("Bottom of turn", 2.0, 1198.5),
        ("Stop", 2.0, 1198.5),
      )

      val option = ChartOption.option(state, Some(metrics), Some(swoop))
      val actual = markersOf(option)

      actual shouldBe expected
    }

    "omits the swoop points when no swoop is detected" in {
      val expected = List("Takeoff", "Exit", "Canopy", "Landing")

      val option = ChartOption.option(state, Some(metrics), None)
      val actual = markersOf(option).map(_._1)

      actual shouldBe expected
    }

    "omits the pattern entry marker from the chart" in {
      val option = ChartOption.option(state, Some(metrics), Some(swoop))
      val actual = markersOf(option).map(_._1)

      actual should not contain "Pattern entry"
    }

    "locates markers by timestamp when the data is cut" in {
      val expected = List(
        ("Turn initiation", -99.0, 1300.0),
        ("Rollout start", -98.0, 1300.0),
        ("Bottom of turn", -98.0, 1300.0),
        ("Stop", -98.0, 1300.0),
        ("Takeoff", 0.0, 1000.0),
        ("Exit", 1.0, 990.0),
        ("Canopy", 1.0, 990.0),
        ("Landing", 2.0, 980.0),
        ("Dive point", 2.0, 980.0),
      )

      val option = ChartOption.option(cutState, None, Some(cutSwoop))
      val actual = markersOf(option)

      actual shouldBe expected
    }

  }

  "ChartHtml page" - {

    "inlines the chart library and the interaction script" in {
      val page = pageOf(state, Some(metrics), None)

      List(
        "Licensed to the Apache Software Foundation",
        "function initChart(option)",
        "initChart(option);",
        "function compact(value)",
        "function applyAxes()",
        "axisSignificantDigits = 4",
        "var chartDefaults = ",
        "var optionGz = ",
      ).foreach(fragment => page should include(fragment))
    }

    "works offline without any cdn reference" in {
      val page = pageOf(state, Some(metrics), None)

      page.toLowerCase should not include "cdn."
      page.toLowerCase should not include "plotly"
    }

    "renders the chart, toolbar and info containers" in {
      val page = pageOf(state, Some(metrics), None)

      List(
        "id=\"chart\"",
        "id=\"pill-zoom\"",
        "id=\"pill-measure\"",
        "id=\"pill-exit\"",
        "id=\"pill-ground\"",
        "<input type=\"checkbox\" id=\"points-toggle\">",
        "id=\"unit-height\"",
        "id=\"unit-speed\"",
      )
        .foreach(container => page should include(container))
      page should not include "id=\"stats\""
    }

    "titles the page with the charted file" in {
      val page = pageOf(state, Some(metrics), None)

      List("<title>Flight Analysis - flysight_sample.csv</title>", "<h1>Flight Analysis - flysight_sample.csv</h1>")
        .foreach(fragment => page should include(fragment))
    }

    "renders the flight information as tables" in {
      val page = pageOf(state, Some(metrics), Some(swoop))

      List(
        "Detected Flight Stages",
        "Swoop Analysis",
        "<th>Altitude (m)</th>",
        "<tr><td>Pattern entry</td><td>1</td><td>1,200.00</td></tr>",
        "1,200.00",
        "315.00° Right",
      ).foreach(fragment => page should include(fragment))
      val info = page.slice(page.indexOf("Detected Flight Stages"), page.indexOf("<script>"))
      "<table>".r.findAllIn(info).size shouldBe 2
      page should not include "<li>"
    }

    "reports the undetected stages" in {
      val empty = state.copy(stages = FlightEvents.empty)
      val page  = pageOf(empty, None, None)

      List("<td colspan=\"2\">Not detected</td>", "Takeoff", "Landing").foreach(fragment => page should include(fragment))
    }

    "embeds the compressed option payload" in {
      val page     = pageOf(state, Some(metrics), None)
      val captured = "var optionGz = \"([A-Za-z0-9+/=]+)\"".r.findFirstMatchIn(page)

      captured.isDefined shouldBe true
      ChartPayload.gunzipBase64(captured.get.group(1)) shouldBe ChartPayload.roundNumbers(
        ChartOption.option(state, Some(metrics), None),
      ).noSpaces
    }

    "states the default exit time and ground level" in {
      val page     = pageOf(state, Some(metrics), None)
      val captured = "var chartDefaults = (\\{[^}]+\\})".r.findFirstMatchIn(page)

      captured.isDefined shouldBe true
      captured.get.group(1) shouldBe """{"exit":1.0,"ground":1198.5}"""
    }

  }

  private def pageOf[A](
    state: ChartState[A],
    metrics: Option[Vector[FlightMetric]],
    swoop: Option[SwoopData],
  ): String =
    ChartHtml.build(state, metrics, swoop, "flysight_sample.csv")

  private def boundsOf(values: Vector[Double]): (Double, Double) =
    val pad = math.max((values.max - values.min) * 0.02, 0.1)
    (values.min - pad, values.max + pad)

  private def traceAxis(unit: String, bound: (Double, Double), on: Boolean, color: String): Json =
    Json.obj(
      "type" -> "value".asJson,
      "name" -> unit.asJson,
      "nameLocation" -> "start".asJson,
      "position" -> "left".asJson,
      "offset" -> 0.asJson,
      "show" -> on.asJson,
      "min" -> bound._1.asJson,
      "max" -> bound._2.asJson,
      "axisLine" -> Json.obj("onZero" -> Json.False, "lineStyle" -> Json.obj("color" -> color.asJson)),
      "axisLabel" -> Json.obj("color" -> color.asJson),
      "axisTick" -> Json.obj("show" -> Json.False),
      "axisPointer" -> Json.obj("label" -> Json.obj("show" -> Json.False)),
      "nameTextStyle" -> Json.obj("color" -> color.asJson, "fontSize" -> 10.asJson),
      "splitLine" -> Json.obj("show" -> Json.False),
    )

  private def field(option: Json, name: String): Json =
    option.hcursor.downField(name).focus.getOrElse(Json.Null)

  private def seriesOf(option: Json): List[Json] =
    option.hcursor.downField("series").as[List[Json]].getOrElse(Nil)

  private def configsOf(names: List[(String, Int)]): List[(String, Int, String)] =
    names.zip(palette).map { case ((name, axis), color) => (name, axis, color) }

  private def configOf(series: Json): (String, Int, String) =
    (
      series.hcursor.get[String]("name").getOrElse(""),
      series.hcursor.get[Int]("yAxisIndex").getOrElse(-1),
      series.hcursor.downField("lineStyle").get[String]("color").getOrElse(""),
    )

  private def markersOf(option: Json): List[(String, Double, Double)] =
    seriesOf(option)
      .flatMap(series => series.hcursor.downField("data").as[List[Json]].getOrElse(Nil))
      .collect { case marker if marker.isObject => marker }
      .map { marker =>
        val value = marker.hcursor.downField("value").as[List[Double]].getOrElse(Nil)
        (marker.hcursor.get[String]("name").getOrElse(""), value(0), value(1))
      }

  private def legendOf(names: List[String], enabled: List[String]): Json =
    Json.obj(
      "data"     -> Json.fromValues(names.map(_.asJson)),
      "selected" -> Json.fromFields(names.map(name => name -> enabled.contains(name).asJson)),
    )

  private def metricAt(second: Int): FlightMetric =
    FlightMetric(
      time = at.plusSeconds(second.toLong),
      altitude = 1200.5 - second,
      verticalSpeed = 40.0,
      horizontalSpeed = 30.0,
      totalSpeed = 50.0,
      course = 315.0,
      courseRate = 12.5,
      courseAccuracy = None,
      courseAccuracyEstimate = None,
      glideRatio = 0.75,
      liftCoefficient = 0.4,
      dragCoefficient = 0.1,
      diveAngle = 45.0,
      diveRate = -3.5,
      turnRate = 90.0,
      speedAccuracy = None,
      horizontalAccuracy = None,
      verticalAccuracy = None,
      satellites = None,
      acceleration = GeoVector(1.5, -2.5, 9.8),
      accelerationMagnitude = 10.4,
      accelerationAlongTrack = 9.5,
      accelerationCrossTrack = 0.5,
      totalEnergy = 2500.0,
      energyRate = -125.0,
      sphericalErrorProbably = None,
      speedScoreAccuracy = None,
    )

}
