package com.colofabrix.scala.beesight.output

import com.colofabrix.scala.beesight.detection.config.{ Altitude, Angle, Speed }
import com.colofabrix.scala.beesight.detection.model.*
import java.time.{ Duration, Instant }
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class ChartGeneratorSpec extends AnyFreeSpec with Matchers {

  private val at    = Instant.parse("2026-01-15T10:00:00Z")
  private val point = EventPoint(0, 1200.0, at, GeoVector(0.0, 0.0, 40.0))
  private val events = FlightEvents(
    takeoff = Some(point.copy(index = 0)),
    freefall = Some(point.copy(index = 1)),
    canopy = Some(point.copy(index = 2)),
    landing = Some(point.copy(index = 2)),
  )
  private val state = ChartState(
    times = Vector(at, at.plusSeconds(1), at.plusSeconds(2)),
    altitudes = Vector(1200.0, 1100.0, 1000.0),
    velDs = Vector(40.0, 42.0, 44.0),
    stages = events,
  )
  private val metrics = Vector(metricAt(0), metricAt(1), metricAt(2))
  private val swoop = SwoopData(
    turnInitiation = point.copy(index = 1),
    rolloutStart = point.copy(index = 2),
    maxVerticalSpeed = SpeedAtAltitude(3, Speed(60.0), Altitude(1800.0)),
    maxTotalSpeed = SpeedAtAltitude(4, Speed(75.0), Altitude(1500.0)),
    maxHorizontalSpeed = SpeedAtAltitude(5, Speed(30.0), Altitude(900.0)),
    degreesOfRotation = Angle(315.0),
    turnDirection = TurnDirection.Right,
    timeToExecuteTurn = Duration.ofMillis(3750),
    ground = Altitude(100.0),
    patternEntry = point.copy(index = 1),
    divePoint = point.copy(index = 2),
    stop = point.copy(index = 2),
    bottomOfTurn = point.copy(index = 2),
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

  "ChartHtml" - {

    "renders every metric trace when metrics are available" in {
      val html = ChartHtml.build(state, Some(metrics), None)

      List(
        "Altitude (m)",
        "Vertical speed (m/s)",
        "Horizontal speed (m/s)",
        "Total speed (m/s)",
        "Glide ratio",
        "Lift coefficient",
        "Drag coefficient",
        "Dive angle (deg)",
        "Dive rate (deg/s)",
        "Turn rate (deg/s)",
        "Acceleration H (m/s²)",
        "Acceleration V (m/s²)",
        "Total energy (J/kg)",
      ).foreach(trace => html should include(trace))
    }

    "renders only the basic traces when metrics are missing" in {
      val html = ChartHtml.build(state, None, None)

      html should include("Altitude (m)")
      html should include("Vertical speed (m/s)")
      html should not include "Glide ratio"
    }

    "ticks only the minimal set of traces on load" in {
      val full  = ChartHtml.build(state, Some(metrics), None)
      val basic = ChartHtml.build(state, None, None)

      """'[^']+': true""".r.findAllIn(full).size shouldBe 4
      """'[^']+': true""".r.findAllIn(basic).size shouldBe 2
    }

    "marks the detected jump events" in {
      val html = ChartHtml.build(state, Some(metrics), None)

      List("Takeoff", "Exit", "Canopy", "Landing").foreach(marker => html should include(marker))
    }

    "marks the swoop points when the swoop analysis is available" in {
      val html = ChartHtml.build(state, Some(metrics), Some(swoop))

      List("Pattern entry", "Dive point", "Turn initiation", "Rollout start", "Bottom of turn", "Stop")
        .foreach(marker => html should include(marker))
    }

    "omits the swoop points when no swoop is detected" in {
      val html = ChartHtml.build(state, Some(metrics), None)

      html should not include "Pattern entry"
    }

    "locates markers by timestamp when the data is cut" in {
      val html = ChartHtml.build(cutState, None, Some(cutSwoop))

      List("value: [0.0, 1200.0]", "value: [1.0, 1200.0]", "value: [2.0, 1200.0]")
        .foreach(markerValue => html should include(markerValue))
    }

    "plots against elapsed time" in {
      val html = ChartHtml.build(state, Some(metrics), None)

      html should include("Elapsed time (s)")
    }

  }

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
