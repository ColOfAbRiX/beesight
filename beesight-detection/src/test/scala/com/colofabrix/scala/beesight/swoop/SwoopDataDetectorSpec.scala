package com.colofabrix.scala.beesight.swoop

import cats.effect.IO
import com.colofabrix.scala.beesight.IOValues
import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.model.*
import java.lang.Math.*
import java.time.{
  Duration,
  Instant
}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class SwoopDataDetectorSpec extends AnyWordSpec with Matchers with IOValues {

  "SwoopDataDetector" should {

    "detect swoop metrics from a clean swoop profile" in {
      val data   = swoopProfile(10, 5, 15, 25)
      val events = flightEvents(35)

      val expected =
        SwoopData(
          turnInitiation = EventPoint(10, 150.0),
          rolloutStart = EventPoint(15, 100.0),
          maxVerticalSpeed = 17.0,
          maxTotalSpeed = 54.483942588619634,
          maxHorizontalSpeed = 54.44722215136416,
          degreesOfRotation = 0.0,
          timeToExecuteTurn = Duration.ofSeconds(5),
        )

      val actual = detect(data, events)

      actual shouldBe Some(expected)
    }

    "return None when no canopy event" in {
      val data   = Vector(dataPoint(0, 1000, 10, 10, 2), dataPoint(1, 990, 10, 10, 2))
      val events = FlightEvents.empty

      val actual = detect(data, events)

      actual shouldBe None
    }

    "return None when no landing event" in {
      val data   = Vector(dataPoint(0, 1000, 10, 10, 2), dataPoint(1, 990, 10, 10, 2))
      val events = FlightEvents.empty.copy(canopy = Some(EventPoint(0, 1000.0)))

      val actual = detect(data, events)

      actual shouldBe None
    }

    "return None when peak vertical speed is below threshold" in {
      val data = (0 to 20)
        .map { i =>
          dataPoint(10 + i, 1000.0 - i * 5, 10.0, 10.0, 2.0 + i * 0.1)
        }
        .toVector

      val events = flightEvents(30)

      val actual = detect(data, events)

      actual shouldBe None
    }

    "skip practice swoops and detect the final swoop" in {
      val turnStart1 = 3
      val peak1      = 5
      val steady     = 8
      val turnStart2 = 12
      val peak2      = 18
      val landingIdx = 25

      val data = (0 to landingIdx)
        .map { i =>
          val altitude = 1500.0 - i * 10.0

          val vz =
            if i < turnStart1 then 2.0
            else if i < peak1 then 2.0 + (i - turnStart1) * 2.0
            else if i == peak1 then 6.0
            else if i < steady then 6.0 - (i - peak1) * 2.0
            else if i < turnStart2 then 2.0
            else if i < peak2 then 2.0 + (i - turnStart2) * 1.5
            else if i == peak2 then 2.0 + (peak2 - turnStart2) * 1.5
            else 2.0 + (peak2 - turnStart2) * 1.5 - (i - peak2) * 1.5

          val horizontal = 15.0 + (if i > turnStart2 then (i - turnStart2) * 2.0 else 0.0)

          dataPoint(10 + i, max(0.0, altitude), horizontal * 0.7, horizontal * 0.7, max(0.1, vz))
        }
        .toVector

      val events = flightEvents(10 + landingIdx)

      val expected =
        SwoopData(
          turnInitiation = EventPoint(12, 130.0),
          rolloutStart = EventPoint(18, 70.0),
          maxVerticalSpeed = 11.0,
          maxTotalSpeed = 40.591008856642134,
          maxHorizontalSpeed = 40.587929240107826,
          degreesOfRotation = 0.0,
          timeToExecuteTurn = Duration.ofSeconds(6),
        )

      val actual = detect(data, events)

      actual shouldBe Some(expected)
    }

    "compute maxVerticalSpeed >= minSwoopVerticalSpeed" in {
      val data   = swoopProfile(10, 5, 15, 25)
      val events = flightEvents(35)

      val expected =
        SwoopData(
          turnInitiation = EventPoint(10, 150.0),
          rolloutStart = EventPoint(15, 100.0),
          maxVerticalSpeed = 17.0,
          maxTotalSpeed = 54.483942588619634,
          maxHorizontalSpeed = 54.44722215136416,
          degreesOfRotation = 0.0,
          timeToExecuteTurn = Duration.ofSeconds(5),
        )

      val actual = detect(data, events)

      actual shouldBe Some(expected)
    }

    "compute maxTotalSpeed >= maxVerticalSpeed" in {
      val data   = swoopProfile(10, 5, 15, 25)
      val events = flightEvents(35)

      val expected =
        SwoopData(
          turnInitiation = EventPoint(10, 150.0),
          rolloutStart = EventPoint(15, 100.0),
          maxVerticalSpeed = 17.0,
          maxTotalSpeed = 54.483942588619634,
          maxHorizontalSpeed = 54.44722215136416,
          degreesOfRotation = 0.0,
          timeToExecuteTurn = Duration.ofSeconds(5),
        )

      val actual = detect(data, events)

      actual shouldBe Some(expected)
    }

    "compute positive degreesOfRotation for a turning swoop" in {
      val turnStart  = 5
      val peakIdx    = 15
      val landingIdx = 25

      val data = (0 to landingIdx)
        .map { i =>
          val altitude = 1500.0 - i * 10.0
          val vz       =
            if i < turnStart then 2.0
            else if i < peakIdx then 2.0 + (i - turnStart) * 1.5
            else if i == peakIdx then 2.0 + (peakIdx - turnStart) * 1.5
            else 2.0 + (peakIdx - turnStart) * 1.5 - (i - peakIdx) * 1.5

          val headingRad =
            if i < turnStart then 0.0
            else (i - turnStart).toDouble / (landingIdx - turnStart) * Math.PI / 2

          val horizontal =
            if i < turnStart then 15.0
            else 15.0 + (i - turnStart) * 2.0

          val north = horizontal * Math.cos(headingRad)
          val east  = horizontal * Math.sin(headingRad)

          dataPoint(10 + i, max(0.0, altitude), north, east, max(0.1, vz))
        }
        .toVector

      val events = flightEvents(10 + landingIdx)

      val expected =
        SwoopData(
          turnInitiation = EventPoint(10, 150.0),
          rolloutStart = EventPoint(15, 100.0),
          maxVerticalSpeed = 17.0,
          maxTotalSpeed = 55.036351623268054,
          maxHorizontalSpeed = 55.0,
          degreesOfRotation = 22.5,
          timeToExecuteTurn = Duration.ofSeconds(5),
        )

      val actual = detect(data, events)

      actual shouldBe Some(expected)
    }

    "compute timeToExecuteTurn as positive duration" in {
      val data   = swoopProfile(10, 5, 15, 25)
      val events = flightEvents(35)

      val expected =
        SwoopData(
          turnInitiation = EventPoint(10, 150.0),
          rolloutStart = EventPoint(15, 100.0),
          maxVerticalSpeed = 17.0,
          maxTotalSpeed = 54.483942588619634,
          maxHorizontalSpeed = 54.44722215136416,
          degreesOfRotation = 0.0,
          timeToExecuteTurn = Duration.ofSeconds(5),
        )

      val actual = detect(data, events)

      actual shouldBe Some(expected)
    }

  }

  private def dataPoint(time: Long, altitude: Double, north: Double, east: Double, vertical: Double): DataPoint =
    DataPoint(t(time), altitude, GeoVector(north, east, vertical))

  private def flightEvents(landingIndex: Long, freefallIndex: Option[Long] = None): FlightEvents =
    FlightEvents(
      takeoff = None,
      freefall = freefallIndex.map { i => EventPoint(i, 3000.0) },
      canopy = Some(EventPoint(10, 1500.0)),
      landing = Some(EventPoint(landingIndex, 0.0)),
    )

  private def swoopProfile(
    canopyStart: Int,
    turnStart: Int,
    peakIdx: Int,
    landingIdx: Int,
  ): Vector[DataPoint] =
    require(turnStart > 0 && peakIdx > turnStart && landingIdx > peakIdx)

    (0 to landingIdx)
      .map { i =>
        val altitude = 1500.0 - i * 10.0
        val vz       =
          if i < turnStart then 2.0
          else if i < peakIdx then 2.0 + (i - turnStart) * 1.5
          else if i == peakIdx then 2.0 + (peakIdx - turnStart) * 1.5
          else 2.0 + (peakIdx - turnStart) * 1.5 - (i - peakIdx) * 1.5

        val horizontal =
          if i < turnStart then 15.0
          else 15.0 + (i - turnStart) * 2.0

        val north = horizontal * 0.7
        val east  = horizontal * 0.7

        dataPoint(canopyStart + i, max(0.0, altitude), north, east, max(0.1, vz))
      }
      .toVector

  private val baseTime = 
    Instant.parse("2024-01-01T12:00:00Z")

  private def t(seconds: Long): Instant =
    baseTime.plusSeconds(seconds)

  private def detect(data: Vector[DataPoint], events: FlightEvents): Option[SwoopData] =
    SwoopDataDetector(DetectionConfig.default)
    .detect(events, fs2.Stream.emits[IO, DataPoint](data))
    .result()

}
