package com.colofabrix.scala.beesight.jumpevents.model

import com.colofabrix.scala.beesight.config.*
import com.colofabrix.scala.beesight.jumpevents.model.JumpEventsConfigActions.*
import com.colofabrix.scala.beesight.model.EventType
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class DetectorConfigActionsSpec extends AnyFreeSpec with Matchers {

  private val config = BeesightConfig.default

  "JumpEventsConfigActions" - {

    "getEventConfigs" - {

      "should return TakeoffConfig for Takeoff event" in {
        config.jumpevents.getEventConfigs(EventType.Takeoff) shouldBe config.jumpevents.takeoff
      }

      "should return FreefallConfig for Freefall event" in {
        config.jumpevents.getEventConfigs(EventType.Freefall) shouldBe config.jumpevents.freefall
      }

      "should return CanopyConfig for Canopy event" in {
        config.jumpevents.getEventConfigs(EventType.Canopy) shouldBe config.jumpevents.canopy
      }

      "should return LandingConfig for Landing event" in {
        config.jumpevents.getEventConfigs(EventType.Landing) shouldBe config.jumpevents.landing
      }

      "should return an EventConfigs for every EventType" in
      EventType.values.foreach { event =>
        noException should be thrownBy config.jumpevents.getEventConfigs(event)
      }

    }

    "getBackwindowSize" - {

      "should return maximum of backtrack and smoothing window sizes" in
      EventType.values.foreach { event =>
        val eventConfig = config.jumpevents.getEventConfigs(event)
        val expected    = Math.max(eventConfig.backtrackWindowSize.value, eventConfig.smoothingWindowSize.value)
        config.jumpevents.getBackwindowSize(event) shouldBe expected
      }

      "should return at least the smoothing window size" in
      EventType.values.foreach { event =>
        val eventConfig = config.jumpevents.getEventConfigs(event)
        config.jumpevents.getBackwindowSize(event) should be >= (eventConfig.smoothingWindowSize.value: Int)
      }

      "should return at least the backtrack window size" in
      EventType.values.foreach { event =>
        val eventConfig = config.jumpevents.getEventConfigs(event)
        config.jumpevents.getBackwindowSize(event) should be >= (eventConfig.backtrackWindowSize.value: Int)
      }

    }

    "eventConfigsAsList" - {

      "should return exactly 4 configs" in {
        config.jumpevents.eventConfigsAsList.size shouldBe 4
      }

      "should contain all event configs" in {
        val configs = config.jumpevents.eventConfigsAsList
        configs should contain(config.jumpevents.takeoff)
        configs should contain(config.jumpevents.freefall)
        configs should contain(config.jumpevents.canopy)
        configs should contain(config.jumpevents.landing)
      }

    }

  }

}
