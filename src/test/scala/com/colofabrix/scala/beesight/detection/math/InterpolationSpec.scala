package com.colofabrix.scala.beesight.detection.math

import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.model.InputFlightRow
import java.time.Instant
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class InterpolationSpec extends AnyFreeSpec with Matchers {

  "Interpolation" - {

    "interpolate Double values" - {

      "should return x1 when n=0" in {
        val result = Interpolation.interpolate(10.0, 20.0, steps = 10, n = 0)
        result shouldBe 10.0
      }

      "should return x2 when n=steps" in {
        val result = Interpolation.interpolate(10.0, 20.0, steps = 10, n = 10)
        result shouldBe 20.0
      }

      "should return midpoint when n=steps/2" in {
        val result = Interpolation.interpolate(10.0, 20.0, steps = 10, n = 5)
        result shouldBe 15.0
      }

      "should interpolate 1/4 of the way" in {
        val result = Interpolation.interpolate(0.0, 100.0, steps = 4, n = 1)
        result shouldBe 25.0
      }

      "should handle negative values" in {
        val result = Interpolation.interpolate(-50.0, 50.0, steps = 10, n = 5)
        result shouldBe 0.0
      }

    }

    "interpolate InputFlightRow" - {

      "should return p1 values when time equals p1.time" in {
        val p1 = point(1, verticalSpeed = 10.0)
        val p2 = point(3, verticalSpeed = 30.0)

        val result = Interpolation.interpolate(p1, p2, Instant.ofEpochSecond(1))

        result.verticalSpeed shouldBe 10.0
      }

      "should return p2 values when time equals p2.time" in {
        val p1 = point(1, verticalSpeed = 10.0)
        val p2 = point(3, verticalSpeed = 30.0)

        val result = Interpolation.interpolate(p1, p2, Instant.ofEpochSecond(3))

        result.verticalSpeed shouldBe 30.0
      }

      "should return midpoint values when time is midway" in {
        val p1 = point(1, verticalSpeed = 10.0)
        val p2 = point(3, verticalSpeed = 30.0)

        val result = Interpolation.interpolate(p1, p2, Instant.ofEpochSecond(2))

        result.verticalSpeed shouldBe 20.0
      }

      "should interpolate all velocity components" in {
        val p1 = point(0, northSpeed = 0.0, eastSpeed = 0.0, verticalSpeed = 0.0)
        val p2 = point(10, northSpeed = 100.0, eastSpeed = 50.0, verticalSpeed = -20.0)

        val result = Interpolation.interpolate(p1, p2, Instant.ofEpochSecond(5))

        result.northSpeed shouldBe 50.0
        result.eastSpeed shouldBe 25.0
        result.verticalSpeed shouldBe -10.0
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

        result.verticalSpeed shouldBe 10.0
      }

      "should interpolate spike boundary correctly" in {
        val oldest = point(1, verticalSpeed = 10.0)
        val newest = point(5, verticalSpeed = 100.0)

        val result = Interpolation.interpolate(oldest, newest, Instant.ofEpochSecond(2))

        result.verticalSpeed shouldBe 32.5
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
  ): InputFlightRow[Unit] =
    InputFlightRow(
      time = Instant.ofEpochSecond(seconds),
      altitude = altitude,
      northSpeed = northSpeed,
      eastSpeed = eastSpeed,
      verticalSpeed = verticalSpeed,
      source = (),
    )

}
