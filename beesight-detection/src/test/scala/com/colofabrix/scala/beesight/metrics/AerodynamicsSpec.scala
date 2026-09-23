package com.colofabrix.scala.beesight.metrics

import com.colofabrix.scala.beesight.config.MetricsConfig
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class AerodynamicsSpec extends AnyFreeSpec with Matchers {

  private val config       = MetricsConfig(slopeHalfWindow = 4, mass = 90.0, planformArea = 10.0)
  private val aerodynamics = Aerodynamics(config)

  "Aerodynamics" - {

    "liftCoefficient" - {

      "follows the quasi-steady glide equilibrium" in {
        val diveAngle  = 20.0
        val totalSpeed = 15.0
        val altitude   = 500.0
        val airDensity = 1.225 * Math.pow(1.0 - 2.25577e-5 * altitude, 4.25588)
        val expected   =
          2.0 * config.mass * 9.80665 * Math.cos(Math.toRadians(diveAngle)) /
          (airDensity * totalSpeed * totalSpeed * config.planformArea)

        aerodynamics.liftCoefficient(diveAngle, totalSpeed, altitude) shouldBe expected +- 1e-9
      }

      "is 0 when not moving" in {
        aerodynamics.liftCoefficient(20.0, 0.0, 0.0) shouldBe 0.0
      }

      "grows as the air thins with altitude" in {
        val atSeaLevel      = aerodynamics.liftCoefficient(20.0, 15.0, 0.0)
        val atTwoKilometres = aerodynamics.liftCoefficient(20.0, 15.0, 2000.0)

        atTwoKilometres should be > atSeaLevel
      }

    }

    "dragCoefficient" - {

      "follows the quasi-steady glide equilibrium" in {
        val diveAngle  = 20.0
        val totalSpeed = 15.0
        val altitude   = 500.0
        val airDensity = 1.225 * Math.pow(1.0 - 2.25577e-5 * altitude, 4.25588)
        val expected   =
          2.0 * config.mass * 9.80665 * Math.sin(Math.toRadians(diveAngle)) /
          (airDensity * totalSpeed * totalSpeed * config.planformArea)

        aerodynamics.dragCoefficient(diveAngle, totalSpeed, altitude) shouldBe expected +- 1e-9
      }

      "is 0 for a horizontal flight path" in {
        aerodynamics.dragCoefficient(0.0, 15.0, 0.0) shouldBe 0.0 +- 1e-12
      }

      "is 0 when not moving" in {
        aerodynamics.dragCoefficient(20.0, 0.0, 0.0) shouldBe 0.0
      }

    }

  }

}
