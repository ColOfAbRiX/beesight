package com.colofabrix.scala.beesight.metrics

import com.colofabrix.scala.beesight.metrics.computations.PerPointMetrics
import com.colofabrix.scala.beesight.model.GeoVector
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class PerPointMetricsSpec extends AnyFreeSpec with Matchers {

  private val LevelFlight  = GeoVector(north = 30.0, east = 0.0, vertical = 0.0)
  private val Descending   = GeoVector(north = 30.0, east = 0.0, vertical = 10.0)
  private val Climbing     = GeoVector(north = 30.0, east = 0.0, vertical = -10.0)
  private val VerticalDive = GeoVector(north = 0.0, east = 0.0, vertical = 10.0)

  "PerPointMetrics" - {

    "diveAngle" - {

      "is 0 for level flight" in {
        PerPointMetrics.diveAngle(LevelFlight) shouldBe 0.0 +- 1e-9
      }

      "is positive when descending" in {
        PerPointMetrics.diveAngle(Descending) shouldBe Math.toDegrees(Math.atan2(10.0, 30.0)) +- 1e-9
      }

      "is negative when climbing" in {
        PerPointMetrics.diveAngle(Climbing) shouldBe Math.toDegrees(Math.atan2(-10.0, 30.0)) +- 1e-9
      }

      "is 90 for a vertical descent" in {
        PerPointMetrics.diveAngle(VerticalDive) shouldBe 90.0 +- 1e-9
      }

    }

    "glideRatio" - {

      "is the horizontal distance per unit of descent" in {
        PerPointMetrics.glideRatio(Descending) shouldBe 3.0
      }

      "is 0 when not descending" in {
        PerPointMetrics.glideRatio(LevelFlight) shouldBe 0.0
      }

      "is negative when climbing" in {
        PerPointMetrics.glideRatio(Climbing) shouldBe -3.0
      }

    }

    "course" - {

      "is 0 due north" in {
        PerPointMetrics.course(LevelFlight) shouldBe 0.0 +- 1e-9
      }

      "is 90 due east" in {
        PerPointMetrics.course(GeoVector(north = 0.0, east = 30.0, vertical = 0.0)) shouldBe 90.0 +- 1e-9
      }

      "is 180 due south" in {
        PerPointMetrics.course(GeoVector(north = -30.0, east = 0.0, vertical = 0.0)) shouldBe 180.0 +- 1e-9
      }

      "is 270 due west" in {
        PerPointMetrics.course(GeoVector(north = 0.0, east = -30.0, vertical = 0.0)) shouldBe 270.0 +- 1e-9
      }

      "is 45 north-east" in {
        PerPointMetrics.course(GeoVector(north = 30.0, east = 30.0, vertical = 0.0)) shouldBe 45.0 +- 1e-9
      }

      "wraps negative angles into [0, 360)" in {
        PerPointMetrics.course(GeoVector(north = 30.0, east = -30.0, vertical = 0.0)) shouldBe 315.0 +- 1e-9
      }

    }

    "totalEnergy" - {

      "sums the kinetic and the potential energy" in {
        PerPointMetrics.totalEnergy(1000.0, 10.0) shouldBe (50.0 + 9.80665 * 1000.0) +- 1e-9
      }

    }

    "accelerationAlongTrack" - {

      "projects the acceleration onto the direction of travel" in {
        val acceleration = GeoVector(north = 1.0, east = 0.0, vertical = 0.0)

        PerPointMetrics.accelerationAlongTrack(acceleration, LevelFlight) shouldBe 1.0 +- 1e-9
      }

      "is 0 for an acceleration perpendicular to the direction of travel" in {
        val acceleration = GeoVector(north = 0.0, east = 2.0, vertical = 0.0)

        PerPointMetrics.accelerationAlongTrack(acceleration, LevelFlight) shouldBe 0.0 +- 1e-9
      }

      "is 0 when not moving horizontally" in {
        val acceleration = GeoVector(north = 5.0, east = 5.0, vertical = 0.0)

        PerPointMetrics.accelerationAlongTrack(acceleration, GeoVector.zero) shouldBe 0.0
      }

    }

    "accelerationCrossTrack" - {

      "projects the acceleration to the right of the direction of travel" in {
        val acceleration = GeoVector(north = 0.0, east = 2.0, vertical = 0.0)

        PerPointMetrics.accelerationCrossTrack(acceleration, LevelFlight) shouldBe 2.0 +- 1e-9
      }

      "is 0 for an acceleration along the direction of travel" in {
        val acceleration = GeoVector(north = 1.0, east = 0.0, vertical = 0.0)

        PerPointMetrics.accelerationCrossTrack(acceleration, LevelFlight) shouldBe 0.0 +- 1e-9
      }

      "is 0 when not moving horizontally" in {
        val acceleration = GeoVector(north = 5.0, east = 5.0, vertical = 0.0)

        PerPointMetrics.accelerationCrossTrack(acceleration, GeoVector.zero) shouldBe 0.0
      }

    }

  }

}
