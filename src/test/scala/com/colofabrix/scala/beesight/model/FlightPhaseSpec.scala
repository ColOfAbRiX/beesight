package com.colofabrix.scala.beesight.model

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class FlightPhaseSpec extends AnyFreeSpec with Matchers {

  "FlightPhase" - {

    "sequence ordering" - {

      "should assign monotonically increasing sequence numbers" in {
        FlightPhase.BeforeTakeoff.sequence shouldBe 0
        FlightPhase.Climbing.sequence shouldBe 1
        FlightPhase.Freefall.sequence shouldBe 2
        FlightPhase.UnderCanopy.sequence shouldBe 3
        FlightPhase.Landed.sequence shouldBe 4
      }

      "should define all five phases with unique sequence numbers" in {
        val sequences = FlightPhase.values.map(_.sequence).toSet
        sequences.size shouldBe 5
      }

    }

    "fromEvent mapping" - {

      "should map Takeoff event to Climbing phase" in {
        FlightPhase.fromEvent(EventType.Takeoff) shouldBe FlightPhase.Climbing
      }

      "should map Freefall event to Freefall phase" in {
        FlightPhase.fromEvent(EventType.Freefall) shouldBe FlightPhase.Freefall
      }

      "should map Canopy event to UnderCanopy phase" in {
        FlightPhase.fromEvent(EventType.Canopy) shouldBe FlightPhase.UnderCanopy
      }

      "should map Landing event to Landed phase" in {
        FlightPhase.fromEvent(EventType.Landing) shouldBe FlightPhase.Landed
      }

      "should cover all EventType values" in {
        EventType.values.foreach { event =>
          noException should be thrownBy FlightPhase.fromEvent(event)
        }
      }

    }

  }

}
