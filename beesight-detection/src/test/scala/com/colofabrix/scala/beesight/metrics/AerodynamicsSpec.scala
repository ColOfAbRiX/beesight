package com.colofabrix.scala.beesight.metrics

import com.colofabrix.scala.beesight.config.{ Area, Mass, MetricsConfig }
import com.colofabrix.scala.beesight.metrics.computations.*
import com.colofabrix.scala.beesight.model.GeoVector
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class AerodynamicsSpec extends AnyFreeSpec with Matchers {

  private val config       = MetricsConfig(slopeHalfWindow = 4, mass = Mass(90.0), planformArea = Area(2.0))
  private val aerodynamics = Aerodynamics(config)

  // Climbing at 3-4-5 with a vertical acceleration of 3 m/s²: gravity leaves a downward residual
  private val acceleration = GeoVector(north = 0.0, east = 0.0, vertical = 3.0)
  private val speed        = GeoVector(north = 0.0, east = 3.0, vertical = 4.0)
  private val altitude     = 500.0

  /** Dynamic pressure of the fixture at the given altitude, from the ISA barometric formulas. */
  private def dynamicPressure(at: Double): Double = {
    val pressure = 101325.0 * Math.pow(1.0 - 0.0065 * at / 288.15, 9.80665 * 0.0289644 / 8.31447 / 0.0065)
    val density  = pressure / (8.31447 / 0.0289644) / (288.15 - 0.0065 * at)

    density * 5.0 * 5.0 / 2.0
  }

  "Aerodynamics" - {

    "liftCoefficient" - {

      "projects the gravity-corrected acceleration perpendicular to the flight path" in {
        val expected = {
          val accelD    = 3.0 - 9.80665
          val proj      = accelD * 4.0 / 5.0
          val dragEast  = proj * 3.0 / 5.0
          val dragDown  = proj * 4.0 / 5.0
          val liftEast  = 0.0 - dragEast
          val liftDown  = accelD - dragDown
          val accelLift = Math.sqrt(liftEast * liftEast + liftDown * liftDown)

          90.0 * accelLift / dynamicPressure(altitude) / 2.0
        }

        aerodynamics.liftCoefficient(acceleration, speed, altitude) shouldBe expected +- 1e-9
      }

      "is 0 when not moving" in {
        aerodynamics.liftCoefficient(GeoVector(1.0, 2.0, 3.0), GeoVector.zero, 0.0) shouldBe 0.0
      }

      "grows as the air thins with altitude" in {
        val atSeaLevel      = aerodynamics.liftCoefficient(acceleration, speed, 0.0)
        val atTwoKilometres = aerodynamics.liftCoefficient(acceleration, speed, 2000.0)

        atTwoKilometres should be > atSeaLevel
      }

    }

    "dragCoefficient" - {

      "keeps the component of the acceleration along the flight path" in {
        val expected = {
          val accelD    = 3.0 - 9.80665
          val proj      = accelD * 4.0 / 5.0
          val dragEast  = proj * 3.0 / 5.0
          val dragDown  = proj * 4.0 / 5.0
          val accelDrag = Math.sqrt(dragEast * dragEast + dragDown * dragDown)

          90.0 * accelDrag / dynamicPressure(altitude) / 2.0
        }

        aerodynamics.dragCoefficient(acceleration, speed, altitude) shouldBe expected +- 1e-9
      }

      "is 0 when the gravity-corrected acceleration is perpendicular to the flight path" in {
        val acceleration = GeoVector(north = 0.0, east = 0.0, vertical = 9.80665)
        val speed        = GeoVector(north = 0.0, east = 15.0, vertical = 0.0)

        aerodynamics.dragCoefficient(acceleration, speed, 0.0) shouldBe 0.0 +- 1e-9
      }

      "is 0 when not moving" in {
        aerodynamics.dragCoefficient(GeoVector(1.0, 2.0, 3.0), GeoVector.zero, 0.0) shouldBe 0.0
      }

    }

  }

}
