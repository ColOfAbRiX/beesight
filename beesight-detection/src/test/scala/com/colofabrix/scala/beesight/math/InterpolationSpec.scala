package com.colofabrix.scala.beesight.math

import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.readers.*
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

        val result = 10.0.interpolate(20.0)(t1, t2, time)

        result shouldBe 10.0
      }

      "should return x2 when time equals t2" in {
        val t1   = Instant.ofEpochSecond(0)
        val t2   = Instant.ofEpochSecond(10)
        val time = Instant.ofEpochSecond(10)

        val result = 10.0.interpolate(20.0)(t1, t2, time)

        result shouldBe 20.0
      }

      "should return midpoint when time is midway" in {
        val t1   = Instant.ofEpochSecond(0)
        val t2   = Instant.ofEpochSecond(10)
        val time = Instant.ofEpochSecond(5)

        val result = 10.0.interpolate(20.0)(t1, t2, time)

        result shouldBe 15.0
      }

      "should interpolate 1/4 of the way" in {
        val t1   = Instant.ofEpochSecond(0)
        val t2   = Instant.ofEpochSecond(4)
        val time = Instant.ofEpochSecond(1)

        val result = 0.0.interpolate(100.0)(t1, t2, time)

        result shouldBe 25.0
      }

      "should handle negative values" in {
        val x1   = -50.0
        val x2   = 50.0
        val t1   = Instant.ofEpochSecond(0)
        val t2   = Instant.ofEpochSecond(10)
        val time = Instant.ofEpochSecond(5)

        val result = x1.interpolate(x2)(t1, t2, time)

        result shouldBe 0.0
      }

    }

    "interpolate IndexedPoint" - {

      "should return p1 values when time equals p1.time" in {
        val p1 = point(1, verticalSpeed = 10.0)
        val p2 = point(3, verticalSpeed = 30.0)

        val result = p1.interpolate(p2)(Instant.ofEpochSecond(1))

        result shouldBe IndexedPoint(3, Instant.ofEpochSecond(1), 1000.0, GeoVector(0.0, 0.0, 10.0))
      }

      "should return p2 values when time equals p2.time" in {
        val p1 = point(1, verticalSpeed = 10.0)
        val p2 = point(3, verticalSpeed = 30.0)

        val result = p1.interpolate(p2)(Instant.ofEpochSecond(3))

        result shouldBe IndexedPoint(3, Instant.ofEpochSecond(3), 1000.0, GeoVector(0.0, 0.0, 30.0))
      }

      "should return midpoint values when time is midway" in {
        val p1 = point(1, verticalSpeed = 10.0)
        val p2 = point(3, verticalSpeed = 30.0)

        val result = p1.interpolate(p2)(Instant.ofEpochSecond(2))

        result shouldBe IndexedPoint(3, Instant.ofEpochSecond(2), 1000.0, GeoVector(0.0, 0.0, 20.0))
      }

      "should interpolate all velocity components" in {
        val p1 = point(0, northSpeed = 0.0, eastSpeed = 0.0, verticalSpeed = 0.0)
        val p2 = point(10, northSpeed = 100.0, eastSpeed = 50.0, verticalSpeed = -20.0)

        val result = p1.interpolate(p2)(Instant.ofEpochSecond(5))

        result shouldBe IndexedPoint(10, Instant.ofEpochSecond(5), 1000.0, GeoVector(50.0, 25.0, -10.0))
      }

      "should interpolate altitude" in {
        val p1 = point(0, altitude = 1000.0)
        val p2 = point(10, altitude = 500.0)

        val result = p1.interpolate(p2)(Instant.ofEpochSecond(5))

        result.altitude shouldBe 750.0
      }

      "should set time to the interpolated time" in {
        val p1      = point(1)
        val p2      = point(5)
        val midTime = Instant.ofEpochSecond(3)

        val result = p1.interpolate(p2)(midTime)

        result.time shouldBe midTime
      }

      "should handle spike interpolation scenario from despiking" in {
        val oldest = point(1, verticalSpeed = 10.0)
        val newest = point(5, verticalSpeed = 10.0)

        val result = oldest.interpolate(newest)(Instant.ofEpochSecond(2))

        result shouldBe IndexedPoint(5, Instant.ofEpochSecond(2), 1000.0, GeoVector(0.0, 0.0, 10.0))
      }

      "should interpolate spike boundary correctly" in {
        val oldest = point(1, verticalSpeed = 10.0)
        val newest = point(5, verticalSpeed = 100.0)

        val result = oldest.interpolate(newest)(Instant.ofEpochSecond(2))

        result shouldBe IndexedPoint(5, Instant.ofEpochSecond(2), 1000.0, GeoVector(0.0, 0.0, 32.5))
      }

    }

    "interpolate GeoVector directly" - {

      "should interpolate all three components" in {
        val v1   = GeoVector(0.0, 0.0, 0.0)
        val v2   = GeoVector(100.0, 50.0, -20.0)
        val t1   = Instant.ofEpochSecond(0)
        val t2   = Instant.ofEpochSecond(10)
        val time = Instant.ofEpochSecond(5)

        val result = v1.interpolate(v2)(t1, t2, time)

        result.north shouldBe 50.0
        result.east shouldBe 25.0
        result.vertical shouldBe -10.0
      }

      "should return v1 at t1" in {
        val v1 = GeoVector(10.0, 20.0, 30.0)
        val v2 = GeoVector(50.0, 60.0, 70.0)
        val t1 = Instant.ofEpochSecond(0)
        val t2 = Instant.ofEpochSecond(10)

        val result = v1.interpolate(v2)(t1, t2, t1)

        result shouldBe v1
      }

      "should return v2 at t2" in {
        val v1 = GeoVector(10.0, 20.0, 30.0)
        val v2 = GeoVector(50.0, 60.0, 70.0)
        val t1 = Instant.ofEpochSecond(0)
        val t2 = Instant.ofEpochSecond(10)

        val result = v1.interpolate(v2)(t1, t2, t2)

        result shouldBe v2
      }

    }

    "extrapolation beyond time range" - {

      "should extrapolate when time is before t1" in {
        val t1   = Instant.ofEpochSecond(10)
        val t2   = Instant.ofEpochSecond(20)
        val time = Instant.ofEpochSecond(0)

        val result = 10.0.interpolate(20.0)(t1, t2, time)

        result shouldBe 0.0
      }

      "should extrapolate when time is after t2" in {
        val t1   = Instant.ofEpochSecond(0)
        val t2   = Instant.ofEpochSecond(10)
        val time = Instant.ofEpochSecond(20)

        val result = 10.0.interpolate(20.0)(t1, t2, time)

        result shouldBe 30.0
      }

    }

    "zero time delta" - {

      "should produce NaN when t1 equals t2 for Double interpolation" in {
        val t1   = Instant.ofEpochSecond(5)
        val time = Instant.ofEpochSecond(5)

        val result = 10.0.interpolate(20.0)(t1, t1, time)

        result.isNaN shouldBe true
      }

    }

    "interpolate PointKinematics" - {

      "should interpolate kinematics at midpoint" in {
        val k1 =
          PointKinematics(
            point =
              IndexedPoint(0L, Instant.ofEpochSecond(0), 1000.0, GeoVector(north = 10.0, east = 0.0, vertical = 0.0)),
            acceleration = GeoVector(north = 5.0, east = 0.0, vertical = 0.0),
          )
        val k2 =
          PointKinematics(
            point =
              IndexedPoint(1L, Instant.ofEpochSecond(10), 800.0, GeoVector(north = 30.0, east = 0.0, vertical = 0.0)),
            acceleration = GeoVector(north = 15.0, east = 0.0, vertical = 0.0),
          )

        val result = k1.interpolate(k2)(Instant.ofEpochSecond(5))

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
  ): IndexedPoint =
    IndexedPoint(
      idx = seconds,
      time = Instant.ofEpochSecond(seconds),
      altitude = altitude,
      speed = GeoVector(north = northSpeed, east = eastSpeed, vertical = verticalSpeed),
    )

}
