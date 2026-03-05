package com.colofabrix.scala.beesight.detection.math

import com.colofabrix.scala.beesight.collections.*
import com.colofabrix.scala.beesight.collections.NonEmptyWindow.*
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.model.GeoVector
import java.time.Instant
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class KinematicsSpec extends AnyFreeSpec with Matchers {

  "Kinematics" - {

    "compute" - {

      "should calculate positive acceleration in vertical direction" in {
        val prev = point(0, verticalSpeed = 10.0)
        val curr = point(1, verticalSpeed = 20.0)

        val result = Kinematics.compute(prev, curr)

        result.acceleration.vertical shouldBe 10.0
      }

      "should calculate zero acceleration when speed unchanged" in {
        val prev = point(0, verticalSpeed = 50.0)
        val curr = point(1, verticalSpeed = 50.0)

        val result = Kinematics.compute(prev, curr)

        result.acceleration.vertical shouldBe 0.0
      }

      "should calculate negative acceleration (deceleration)" in {
        val prev = point(0, verticalSpeed = 100.0)
        val curr = point(1, verticalSpeed = 50.0)

        val result = Kinematics.compute(prev, curr)

        result.acceleration.vertical shouldBe -50.0
      }

      "should calculate acceleration in all directions" in {
        val prev = point(0, northSpeed = 0.0, eastSpeed = 0.0, verticalSpeed = 0.0)
        val curr = point(1, northSpeed = 10.0, eastSpeed = 20.0, verticalSpeed = 30.0)

        val result = Kinematics.compute(prev, curr)

        result.acceleration.north shouldBe 10.0
        result.acceleration.east shouldBe 20.0
        result.acceleration.vertical shouldBe 30.0
      }

      "should account for time delta in acceleration calculation" in {
        val prev = point(0, verticalSpeed = 0.0)
        val curr = point(2, verticalSpeed = 20.0)

        val result = Kinematics.compute(prev, curr)

        result.acceleration.vertical shouldBe 10.0
      }

      "should halve acceleration for doubled time interval" in {
        val prev1 = point(0, verticalSpeed = 0.0)
        val curr1 = point(1, verticalSpeed = 100.0)

        val prev2 = point(0, verticalSpeed = 0.0)
        val curr2 = point(2, verticalSpeed = 100.0)

        val result1 = Kinematics.compute(prev1, curr1)
        val result2 = Kinematics.compute(prev2, curr2)

        result1.acceleration.vertical shouldBe 100.0
        result2.acceleration.vertical shouldBe 50.0
      }

      "should set result time to current point time" in {
        val prev = point(5)
        val curr = point(10)

        val result = Kinematics.compute(prev, curr)

        result.time shouldBe Instant.ofEpochSecond(10)
      }

      "should set result altitude to current point altitude" in {
        val prev = point(0, altitude = 1000.0)
        val curr = point(1, altitude = 900.0)

        val result = Kinematics.compute(prev, curr)

        result.altitude shouldBe 900.0
      }

      "should set result speed to current point speed vector" in {
        val prev = point(0, northSpeed = 5.0, eastSpeed = 10.0, verticalSpeed = 15.0)
        val curr = point(1, northSpeed = 20.0, eastSpeed = 30.0, verticalSpeed = 40.0)

        val result = Kinematics.compute(prev, curr)

        result.speed shouldBe GeoVector(north = 20.0, east = 30.0, vertical = 40.0)
      }

      "should calculate acceleration magnitude correctly" in {
        val prev = point(0, verticalSpeed = 10.0)
        val curr = point(1, verticalSpeed = 100.0)

        val result = Kinematics.compute(prev, curr)

        result.acceleration.magnitude shouldBe 90.0
      }

      "should show acceleration below threshold for normal flight" in {
        val prev = point(0, verticalSpeed = 10.0)
        val curr = point(1, verticalSpeed = 12.0)

        val result = Kinematics.compute(prev, curr)

        (result.acceleration > 5.0) shouldBe false
      }

      "should show acceleration above threshold for spike" in {
        val prev = point(0, verticalSpeed = 10.0)
        val curr = point(1, verticalSpeed = 100.0)

        val result = Kinematics.compute(prev, curr)

        (result.acceleration > 5.0) shouldBe true
      }

    }

    "zero time delta" - {

      "should produce Infinity acceleration when two points have the same time" in {
        val prev = point(5, verticalSpeed = 10.0)
        val curr = point(5, verticalSpeed = 20.0)

        val result = Kinematics.compute(prev, curr)

        result.acceleration.vertical.isInfinity shouldBe true
      }

    }

    "multi-axis kinematics" - {

      "acceleration should use all three axes independently" in {
        val prev = point(0, northSpeed = 0.0, eastSpeed = 0.0, verticalSpeed = 0.0)
        val curr = point(2, northSpeed = 20.0, eastSpeed = 40.0, verticalSpeed = 60.0)

        val result = Kinematics.compute(prev, curr)

        result.acceleration.north shouldBe 10.0
        result.acceleration.east shouldBe 20.0
        result.acceleration.vertical shouldBe 30.0
      }

    }

    "even-sized smoothing window" - {

      "should compute kinematics from even number of points" in {
        val window = filledWindowOfDataPoints(
          point(0, verticalSpeed = 10.0),
          point(1, verticalSpeed = 20.0),
          point(2, verticalSpeed = 30.0),
          point(3, verticalSpeed = 40.0),
        )
        val curr = point(4, verticalSpeed = 50.0)

        val result = Kinematics.computeSmoothed(window, curr)

        // After enqueue: [20, 30, 40, 50], median is 35
        result.speed.vertical shouldBe 35.0
      }

    }

    "computeSmoothed" - {

      "should use median speed from window" in {
        val window = filledWindowOfDataPoints(
          point(0, verticalSpeed = 10.0),
          point(1, verticalSpeed = 50.0),
          point(2, verticalSpeed = 20.0),
        )
        val curr = point(3, verticalSpeed = 30.0)

        val result = Kinematics.computeSmoothed(window, curr)

        // After enqueue: speeds are [50, 20, 30], median is 30
        result.speed.vertical shouldBe 30.0
      }

      "should calculate acceleration based on time delta" in {
        val window = filledWindowOfDataPoints(
          point(0, verticalSpeed = 10.0),
          point(1, verticalSpeed = 20.0),
          point(2, verticalSpeed = 30.0),
        )
        val curr = point(3, verticalSpeed = 50.0)

        val result = Kinematics.computeSmoothed(window, curr)

        // prev (newest in window) = point(2) with speed 30
        // curr = point(3) with speed 50
        // dt = 1s, acceleration = (50-30)/1 = 20
        result.acceleration.vertical shouldBe 20.0
      }

      "should set result time to current point time" in {
        val window = filledWindowOfDataPoints(
          point(5, verticalSpeed = 10.0),
          point(6, verticalSpeed = 20.0),
          point(7, verticalSpeed = 30.0),
        )
        val curr = point(10, verticalSpeed = 40.0)

        val result = Kinematics.computeSmoothed(window, curr)

        result.time shouldBe Instant.ofEpochSecond(10)
      }

      "should set result altitude to current point altitude" in {
        val window = filledWindowOfDataPoints(
          point(0, altitude = 1000.0),
          point(1, altitude = 950.0),
          point(2, altitude = 900.0),
        )
        val curr = point(3, altitude = 850.0)

        val result = Kinematics.computeSmoothed(window, curr)

        result.altitude shouldBe 850.0
      }

      "should handle window of size 1" in {
        val window = filledWindowOfDataPoints(
          point(0, verticalSpeed = 10.0),
        )
        val curr = point(1, verticalSpeed = 30.0)

        val result = Kinematics.computeSmoothed(window, curr)

        // After enqueue: only curr speed, median is 30
        result.speed.vertical shouldBe 30.0
        result.acceleration.vertical shouldBe 20.0
      }

    }

  }

  private def point(
    seconds: Long,
    altitude: Double = 1000.0,
    northSpeed: Double = 0.0,
    eastSpeed: Double = 0.0,
    verticalSpeed: Double = 0.0,
  ): IndexedDataPoint =
    IndexedDataPoint(
      time = Instant.ofEpochSecond(seconds),
      idx = 0L,
      altitude = altitude,
      speed = GeoVector(north = northSpeed, east = eastSpeed, vertical = verticalSpeed),
    )

  private def filledWindowOfDataPoints(points: IndexedDataPoint*): NonEmptyFilled[IndexedDataPoint] =
    points.tail
      .foldLeft(NonEmptyWindow(points.size, points.head))((w, p) => w.enqueue(p))
      .asInstanceOf[NonEmptyFilled[IndexedDataPoint]]

}
