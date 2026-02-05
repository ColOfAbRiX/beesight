package com.colofabrix.scala.beesight.detection.math

import com.colofabrix.scala.beesight.detection.model.*
import java.time.Instant
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class InterpolationSpec extends AnyFreeSpec with Matchers {

  "Interpolation" - {

    "interpolate Double values" - {

      "should return x1 when time equals t1" in {
        val t1   = Instant.ofEpochSecond(0)
        val t2   = Instant.ofEpochSecond(10)
        val time = Instant.ofEpochSecond(0)

        val result = Interpolation.interpolate(10.0, 20.0)(t1, t2, time)

        result shouldBe 10.0
      }

      "should return x2 when time equals t2" in {
        val t1   = Instant.ofEpochSecond(0)
        val t2   = Instant.ofEpochSecond(10)
        val time = Instant.ofEpochSecond(10)

        val result = Interpolation.interpolate(10.0, 20.0)(t1, t2, time)

        result shouldBe 20.0
      }

      "should return midpoint when time is midway" in {
        val t1   = Instant.ofEpochSecond(0)
        val t2   = Instant.ofEpochSecond(10)
        val time = Instant.ofEpochSecond(5)

        val result = Interpolation.interpolate(10.0, 20.0)(t1, t2, time)

        result shouldBe 15.0
      }

      "should interpolate 1/4 of the way" in {
        val t1   = Instant.ofEpochSecond(0)
        val t2   = Instant.ofEpochSecond(4)
        val time = Instant.ofEpochSecond(1)

        val result = Interpolation.interpolate(0.0, 100.0)(t1, t2, time)

        result shouldBe 25.0
      }

      "should handle negative values" in {
        val t1   = Instant.ofEpochSecond(0)
        val t2   = Instant.ofEpochSecond(10)
        val time = Instant.ofEpochSecond(5)

        val result = Interpolation.interpolate(-50.0, 50.0)(t1, t2, time)

        result shouldBe 0.0
      }

    }

    "interpolate DataPoint" - {

      "should return p1 values when time equals p1.time" in {
        val p1 = point(1, verticalSpeed = 10.0)
        val p2 = point(3, verticalSpeed = 30.0)

        val result = Interpolation.interpolate(p1, p2, Instant.ofEpochSecond(1))

        result.speed.vertical shouldBe 10.0
      }

      "should return p2 values when time equals p2.time" in {
        val p1 = point(1, verticalSpeed = 10.0)
        val p2 = point(3, verticalSpeed = 30.0)

        val result = Interpolation.interpolate(p1, p2, Instant.ofEpochSecond(3))

        result.speed.vertical shouldBe 30.0
      }

      "should return midpoint values when time is midway" in {
        val p1 = point(1, verticalSpeed = 10.0)
        val p2 = point(3, verticalSpeed = 30.0)

        val result = Interpolation.interpolate(p1, p2, Instant.ofEpochSecond(2))

        result.speed.vertical shouldBe 20.0
      }

      "should interpolate all velocity components" in {
        val p1 = point(0, northSpeed = 0.0, eastSpeed = 0.0, verticalSpeed = 0.0)
        val p2 = point(10, northSpeed = 100.0, eastSpeed = 50.0, verticalSpeed = -20.0)

        val result = Interpolation.interpolate(p1, p2, Instant.ofEpochSecond(5))

        result.speed.north shouldBe 50.0
        result.speed.east shouldBe 25.0
        result.speed.vertical shouldBe -10.0
      }

      "should interpolate altitude" in {
        val p1 = point(0, altitude = 1000.0)
        val p2 = point(10, altitude = 500.0)

        val result = Interpolation.interpolate(p1, p2, Instant.ofEpochSecond(5))

        result.altitude shouldBe 750.0
      }

      "should set time to the interpolated time" in {
        val p1      = point(1)
        val p2      = point(5)
        val midTime = Instant.ofEpochSecond(3)

        val result = Interpolation.interpolate(p1, p2, midTime)

        result.time shouldBe midTime
      }

      "should handle spike interpolation scenario from despiking" in {
        val oldest = point(1, verticalSpeed = 10.0)
        val newest = point(5, verticalSpeed = 10.0)

        val result = Interpolation.interpolate(oldest, newest, Instant.ofEpochSecond(2))

        result.speed.vertical shouldBe 10.0
      }

      "should interpolate spike boundary correctly" in {
        val oldest = point(1, verticalSpeed = 10.0)
        val newest = point(5, verticalSpeed = 100.0)

        val result = Interpolation.interpolate(oldest, newest, Instant.ofEpochSecond(2))

        result.speed.vertical shouldBe 32.5
      }

    }

    "interpolate PointKinematics" - {

      "should interpolate kinematics at midpoint" in {
        val k1 =
          PointKinematics(
            time = Instant.ofEpochSecond(0),
            altitude = 1000.0,
            speed = GeoVector(north = 10.0, east = 0.0, vertical = 0.0),
            acceleration = GeoVector(north = 5.0, east = 0.0, vertical = 0.0),
          )
        val k2 =
          PointKinematics(
            time = Instant.ofEpochSecond(10),
            altitude = 800.0,
            speed = GeoVector(north = 30.0, east = 0.0, vertical = 0.0),
            acceleration = GeoVector(north = 15.0, east = 0.0, vertical = 0.0),
          )

        val result = Interpolation.interpolate(k1, k2, Instant.ofEpochSecond(5))

        result.altitude shouldBe 900.0
        result.speed shouldBe GeoVector(north = 20.0, east = 0.0, vertical = 0.0)
        result.acceleration shouldBe GeoVector(north = 10.0, east = 0.0, vertical = 0.0)
      }

    }

  }

  private def point(
    seconds: Long,
    altitude: Double = 1000.0,
    northSpeed: Double = 0.0,
    eastSpeed: Double = 0.0,
    verticalSpeed: Double = 0.0,
  ): DataPoint =
    DataPoint(
      time = Instant.ofEpochSecond(seconds),
      altitude = altitude,
      speed = GeoVector(north = northSpeed, east = eastSpeed, vertical = verticalSpeed),
    )

}
