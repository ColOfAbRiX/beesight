package com.colofabrix.scala.beesight.model

import com.colofabrix.scala.beesight.detection.model.*
import java.time.Instant
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class OutputFlightPointSpec extends AnyFreeSpec with Matchers {

  "FlightEvents.phase" - {

    "should be BeforeTakeoff when no events are set" in {
      val events = FlightEvents.empty
      events.phase shouldBe FlightPhase.BeforeTakeoff
    }

    "should be Climbing when only takeoff is set" in {
      val events =
        FlightEvents
          .empty
          .setEventPoint(EventType.Takeoff, EventPoint(10, 500.0, Instant.EPOCH, GeoVector.zero))

      events.phase shouldBe FlightPhase.Climbing
    }

    "should be Freefall when takeoff and freefall are set" in {
      val events =
        FlightEvents
          .empty
          .setEventPoint(EventType.Takeoff, EventPoint(10, 500.0, Instant.EPOCH, GeoVector.zero))
          .setEventPoint(EventType.Freefall, EventPoint(50, 4000.0, Instant.EPOCH, GeoVector.zero))

      events.phase shouldBe FlightPhase.Freefall
    }

    "should be UnderCanopy when takeoff, freefall, and canopy are set" in {
      val events =
        FlightEvents
          .empty
          .setEventPoint(EventType.Takeoff, EventPoint(10, 500.0, Instant.EPOCH, GeoVector.zero))
          .setEventPoint(EventType.Freefall, EventPoint(50, 4000.0, Instant.EPOCH, GeoVector.zero))
          .setEventPoint(EventType.Canopy, EventPoint(200, 1200.0, Instant.EPOCH, GeoVector.zero))

      events.phase shouldBe FlightPhase.UnderCanopy
    }

    "should be Landed when all events are set" in {
      val events =
        FlightEvents
          .empty
          .setEventPoint(EventType.Takeoff, EventPoint(10, 500.0, Instant.EPOCH, GeoVector.zero))
          .setEventPoint(EventType.Freefall, EventPoint(50, 4000.0, Instant.EPOCH, GeoVector.zero))
          .setEventPoint(EventType.Canopy, EventPoint(200, 1200.0, Instant.EPOCH, GeoVector.zero))
          .setEventPoint(EventType.Landing, EventPoint(350, 50.0, Instant.EPOCH, GeoVector.zero))

      events.phase shouldBe FlightPhase.Landed
    }

    "should prioritize landing over all other events" in {
      val events =
        FlightEvents
          .empty
          .setEventPoint(EventType.Landing, EventPoint(350, 50.0, Instant.EPOCH, GeoVector.zero))

      events.phase shouldBe FlightPhase.Landed
    }

    "should prioritize canopy over freefall and takeoff" in {
      val events =
        FlightEvents
          .empty
          .setEventPoint(EventType.Canopy, EventPoint(200, 1200.0, Instant.EPOCH, GeoVector.zero))

      events.phase shouldBe FlightPhase.UnderCanopy
    }

  }

}
