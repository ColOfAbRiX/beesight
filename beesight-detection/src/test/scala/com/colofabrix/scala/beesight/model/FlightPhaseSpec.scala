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

  }

}
