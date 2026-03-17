package com.colofabrix.scala.beesight.model

import com.colofabrix.scala.beesight.model.OutputFlightPoint.*
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class OutputFlightPointSpec extends AnyFreeSpec with Matchers {

  "OutputFlightPoint" - {

    "phase derivation" - {

      "should be BeforeTakeoff when no events are set" in {
        val point = OutputFlightPoint[String](None, None, None, None, "source")
        point.phase shouldBe FlightPhase.BeforeTakeoff
      }

      "should be Climbing when only takeoff is set" in {
        val point = OutputFlightPoint[String](
          takeoff = Some(FlightEventPoint(10, 500.0)),
          freefall = None,
          canopy = None,
          landing = None,
          source = "source",
        )
        point.phase shouldBe FlightPhase.Climbing
      }

      "should be Freefall when takeoff and freefall are set" in {
        val point = OutputFlightPoint[String](
          takeoff = Some(FlightEventPoint(10, 500.0)),
          freefall = Some(FlightEventPoint(50, 4000.0)),
          canopy = None,
          landing = None,
          source = "source",
        )
        point.phase shouldBe FlightPhase.Freefall
      }

      "should be UnderCanopy when takeoff, freefall, and canopy are set" in {
        val point = OutputFlightPoint[String](
          takeoff = Some(FlightEventPoint(10, 500.0)),
          freefall = Some(FlightEventPoint(50, 4000.0)),
          canopy = Some(FlightEventPoint(200, 1200.0)),
          landing = None,
          source = "source",
        )
        point.phase shouldBe FlightPhase.UnderCanopy
      }

      "should be Landed when all events are set" in {
        val point = OutputFlightPoint[String](
          takeoff = Some(FlightEventPoint(10, 500.0)),
          freefall = Some(FlightEventPoint(50, 4000.0)),
          canopy = Some(FlightEventPoint(200, 1200.0)),
          landing = Some(FlightEventPoint(350, 50.0)),
          source = "source",
        )
        point.phase shouldBe FlightPhase.Landed
      }

      "should prioritize landing over all other events" in {
        val point = OutputFlightPoint[String](
          takeoff = None,
          freefall = None,
          canopy = None,
          landing = Some(FlightEventPoint(350, 50.0)),
          source = "source",
        )
        point.phase shouldBe FlightPhase.Landed
      }

      "should prioritize canopy over freefall and takeoff" in {
        val point = OutputFlightPoint[String](
          takeoff = None,
          freefall = None,
          canopy = Some(FlightEventPoint(200, 1200.0)),
          landing = None,
          source = "source",
        )
        point.phase shouldBe FlightPhase.UnderCanopy
      }

    }

  }

}
