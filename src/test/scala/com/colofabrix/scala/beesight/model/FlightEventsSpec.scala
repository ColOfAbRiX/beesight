package com.colofabrix.scala.beesight.model

import com.colofabrix.scala.beesight.model.FlightEvents.*
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class FlightEventsSpec extends AnyFreeSpec with Matchers {

  "FlightEvents" - {

    "empty factory" - {

      "should have all fields as None" in {
        val events = FlightEvents.empty
        events.takeoff shouldBe None
        events.freefall shouldBe None
        events.canopy shouldBe None
        events.landing shouldBe None
      }

    }

    "setEvent" - {

      "should set takeoff and leave others unchanged" in {
        val point  = FlightEventPoint(10, 500.0)
        val result = FlightEvents.empty.setEvent(EventType.Takeoff, point)
        result.takeoff shouldBe Some(point)
        result.freefall shouldBe None
        result.canopy shouldBe None
        result.landing shouldBe None
      }

      "should set freefall and leave others unchanged" in {
        val point  = FlightEventPoint(50, 4000.0)
        val result = FlightEvents.empty.setEvent(EventType.Freefall, point)
        result.takeoff shouldBe None
        result.freefall shouldBe Some(point)
        result.canopy shouldBe None
        result.landing shouldBe None
      }

      "should set canopy and leave others unchanged" in {
        val point  = FlightEventPoint(200, 1200.0)
        val result = FlightEvents.empty.setEvent(EventType.Canopy, point)
        result.takeoff shouldBe None
        result.freefall shouldBe None
        result.canopy shouldBe Some(point)
        result.landing shouldBe None
      }

      "should set landing and leave others unchanged" in {
        val point  = FlightEventPoint(350, 50.0)
        val result = FlightEvents.empty.setEvent(EventType.Landing, point)
        result.takeoff shouldBe None
        result.freefall shouldBe None
        result.canopy shouldBe None
        result.landing shouldBe Some(point)
      }

      "should accumulate multiple events correctly" in {
        val takeoffPt  = FlightEventPoint(10, 500.0)
        val freefallPt = FlightEventPoint(50, 4000.0)
        val canopyPt   = FlightEventPoint(200, 1200.0)
        val landingPt  = FlightEventPoint(350, 50.0)

        val result =
          FlightEvents.empty
            .setEvent(EventType.Takeoff, takeoffPt)
            .setEvent(EventType.Freefall, freefallPt)
            .setEvent(EventType.Canopy, canopyPt)
            .setEvent(EventType.Landing, landingPt)

        result.takeoff shouldBe Some(takeoffPt)
        result.freefall shouldBe Some(freefallPt)
        result.canopy shouldBe Some(canopyPt)
        result.landing shouldBe Some(landingPt)
      }

      "should overwrite an existing event of the same type" in {
        val first  = FlightEventPoint(10, 500.0)
        val second = FlightEventPoint(15, 550.0)

        val result =
          FlightEvents.empty
            .setEvent(EventType.Takeoff, first)
            .setEvent(EventType.Takeoff, second)

        result.takeoff shouldBe Some(second)
      }

    }

  }

}
