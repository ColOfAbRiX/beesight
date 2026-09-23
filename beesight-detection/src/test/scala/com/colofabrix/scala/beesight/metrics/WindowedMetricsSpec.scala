package com.colofabrix.scala.beesight.metrics

import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.readers.*
import java.time.Instant
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class WindowedMetricsSpec extends AnyFreeSpec with Matchers {

  private val ConstantFlight = Vector(
    point(0, north = 30.0, vertical = 10.0),
    point(1, north = 30.0, vertical = 10.0),
    point(2, north = 30.0, vertical = 10.0),
  )

  "WindowedMetrics" - {

    "acceleration" - {

      "is zero for a constant speed" in {
        WindowedMetrics.acceleration(ConstantFlight) shouldBe GeoVector.zero
      }

      "returns the component-wise least-squares slopes" in {
        val window = Vector(
          point(0, vertical = 10.0),
          point(1, vertical = 15.0),
          point(2, vertical = 20.0),
        )

        WindowedMetrics.acceleration(window) shouldBe GeoVector(north = 0.0, east = 0.0, vertical = 5.0)
      }

    }

    "courseRate" - {

      "is 0 for a constant course" in {
        WindowedMetrics.courseRate(ConstantFlight) shouldBe 0.0 +- 1e-9
      }

      "unwraps the course when it crosses north" in {
        val window = Vector(
          point(0, courseDegrees = 350.0),
          point(1, courseDegrees = 355.0),
          point(2, courseDegrees = 0.0),
        )

        WindowedMetrics.courseRate(window) shouldBe 5.0 +- 1e-6
      }

    }

    "diveRate" - {

      "is 0 for a constant dive angle" in {
        WindowedMetrics.diveRate(ConstantFlight) shouldBe 0.0 +- 1e-9
      }

      "is the slope of the dive angle between two points" in {
        val window = Vector(
          point(0, north = 30.0, vertical = 10.0),
          point(1, north = 30.0, vertical = 20.0),
        )
        val expected = Math.toDegrees(Math.atan2(20.0, 30.0)) - Math.toDegrees(Math.atan2(10.0, 30.0))

        WindowedMetrics.diveRate(window) shouldBe expected +- 1e-9
      }

    }

    "totalSpeedRate" - {

      "is 0 for a constant speed" in {
        WindowedMetrics.totalSpeedRate(ConstantFlight) shouldBe 0.0 +- 1e-9
      }

      "is the slope of the total speed" in {
        val window = Vector(
          point(0, vertical = 10.0),
          point(1, vertical = 20.0),
        )

        WindowedMetrics.totalSpeedRate(window) shouldBe 10.0 +- 1e-9
      }

    }

    "turnRate" - {

      "is 0 for the first point of the window" in {
        WindowedMetrics.turnRate(0, ConstantFlight) shouldBe 0.0
      }

      "is 0 for a constant course" in {
        WindowedMetrics.turnRate(1, ConstantFlight) shouldBe 0.0 +- 1e-9
      }

      "wraps the turn when the course crosses north" in {
        val window = Vector(
          point(0, courseDegrees = 350.0),
          point(1, courseDegrees = 0.0),
        )

        WindowedMetrics.turnRate(1, window) shouldBe 10.0 +- 1e-6
      }

      "is 0 for a non-positive time step" in {
        val window = Vector(
          point(0, courseDegrees = 0.0),
          point(0, courseDegrees = 90.0),
        )

        WindowedMetrics.turnRate(1, window) shouldBe 0.0
      }

    }

  }

  private def point(index: Int, north: Double = 0.0, east: Double = 0.0, vertical: Double = 0.0): TestPoint =
    TestPoint(
      time = Instant.ofEpochMilli(index * 1000),
      altitude = 1000.0,
      speed = GeoVector(north = north, east = east, vertical = vertical),
    )

  private def point(index: Int, courseDegrees: Double): TestPoint =
    val radians = Math.toRadians(courseDegrees)
    point(index, north = 30.0 * Math.cos(radians), east = 30.0 * Math.sin(radians))

}

final private case class TestPoint(
  time: Instant,
  altitude: Double,
  speed: GeoVector,
)

private object TestPoint {

  given PointReader[TestPoint] with {
    def time(point: TestPoint): Instant    = point.time
    def altitude(point: TestPoint): Double = point.altitude
    def speed(point: TestPoint): GeoVector = point.speed
  }

}
