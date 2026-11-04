package com.colofabrix.scala.beesight.output

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
    ("Horizontal speed (m/s)", 1),
    ("Total speed (m/s)", 1),
    ("Glide ratio", 1),
    ("Lift coefficient", 1),
    ("Drag coefficient", 1),
    ("Dive angle (deg)", 1),
    ("Dive rate (deg/s)", 1),
    ("Turn rate (deg/s)", 1),
    ("Acceleration H (m/s²)", 1),
    ("Acceleration V (m/s²)", 1),
    ("Total energy (J/kg)", 1),
  )

  private val basicNames: List[(String, Int)] = List(
    ("Altitude (m)", 0),
    ("Vertical speed (m/s)", 1),
  )

  "ChartHtml option" - {

    "renders every metric trace as a line series" in {
      val expected = configsOf(metricNames)

      val option = ChartHtml.option(state, Some(metrics), None)
      val actual = seriesOf(option).map(configOf)

      actual shouldBe expected
    }

    "renders only the basic traces when metrics are missing" in {
      val expected = configsOf(basicNames)

      val option = ChartHtml.option(state, None, None)
      val actual = seriesOf(option).map(configOf)

      actual shouldBe expected
    }

    "selects only the minimal set of traces on load" in {
      val expectedFull  = legendOf(metricNames.map(_._1), metricNames.take(4).map(_._1))
      val expectedBasic = legendOf(basicNames.map(_._1), basicNames.map(_._1))

      val full  = field(ChartHtml.option(state, Some(metrics), None), "legend")
      val basic = field(ChartHtml.option(state, None, None), "legend")

      full shouldBe expectedFull
      basic shouldBe expectedBasic
    }

    "keeps the core option shape" in {
      val expected = Json.obj(
        "animation" -> Json.False,
        "title"     -> Json.obj("text" -> "Flight Data".asJson),
        "tooltip"   -> Json.obj("trigger" -> "axis".asJson, "axisPointer" -> Json.obj("type" -> "crosshair".asJson)),
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
        "dataZoom"  -> Json.arr(Json.obj("type" -> "inside".asJson)),
        "brush"     -> Json.obj(
          "xAxisIndex"    -> Json.arr(0.asJson),
          "brushMode"     -> "single".asJson,
          "transformable" -> Json.False,
          "brushStyle"    -> Json.obj(
            "color"       -> "rgba(85, 85, 85, 0.08)".asJson,
            "borderColor" -> "#555".asJson,
            "borderWidth" -> 1.asJson,
          ),
        ),
      )

      val option = ChartHtml.option(state, Some(metrics), None)
      val actual = option.asObject.map(_.remove("series").remove("legend").asJson).getOrElse(Json.Null)

      actual shouldBe expected
    }

    "keeps the speed data as coordinate pairs" in {
      val expected = List(List(0.0, 40.0), List(1.0, 40.0), List(2.0, 40.0))

      val option = ChartHtml.option(state, Some(metrics), None)
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

      val option = ChartHtml.option(state, Some(metrics), None)
      val actual = markersOf(option)

      actual shouldBe expected
    }

    "marks the swoop points when the swoop analysis is available" in {
      val expected = List(
        ("Takeoff", 0.0, 1200.5),
        ("Exit", 1.0, 1199.5),
        ("Pattern entry", 1.0, 1199.5),
        ("Turn initiation", 1.0, 1199.5),
        ("Canopy", 2.0, 1198.5),
        ("Landing", 2.0, 1198.5),
        ("Dive point", 2.0, 1198.5),
        ("Rollout start", 2.0, 1198.5),
        ("Bottom of turn", 2.0, 1198.5),
        ("Stop", 2.0, 1198.5),
      )

      val option = ChartHtml.option(state, Some(metrics), Some(swoop))
      val actual = markersOf(option)

      actual shouldBe expected
    }

    "omits the swoop points when no swoop is detected" in {
      val expected = List("Takeoff", "Exit", "Canopy", "Landing")

      val option = ChartHtml.option(state, Some(metrics), None)
      val actual = markersOf(option).map(_._1)

      actual shouldBe expected
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
        ("Pattern entry", 1.0, 990.0),
        ("Landing", 2.0, 980.0),
        ("Dive point", 2.0, 980.0),
      )

      val option = ChartHtml.option(cutState, None, Some(cutSwoop))
      val actual = markersOf(option)

      actual shouldBe expected
    }

  }

  "ChartHtml page" - {

    "inlines the chart library and the interaction script" in {
      val page = ChartHtml.build(state, Some(metrics), None)

      List(
        "Licensed to the Apache Software Foundation",
        "<script>function initChart(option)",
        "initChart(option);",
      ).foreach(fragment => page should include(fragment))
    }

    "works offline without any cdn reference" in {
      val page = ChartHtml.build(state, Some(metrics), None)

      page.toLowerCase should not include "cdn."
      page.toLowerCase should not include "plotly"
    }

    "renders the chart, stats and badge containers" in {
      val page = ChartHtml.build(state, Some(metrics), None)

      List("id=\"chart\"", "id=\"stats\"", "id=\"badge\"").foreach(container => page should include(container))
    }

    "injects the option as a single assignment" in {
      val page = ChartHtml.build(state, Some(metrics), None)

      "var option = ".r.findAllIn(page).size shouldBe 1
      page should include("""var option = {"animation":false""")
    }

  }

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
