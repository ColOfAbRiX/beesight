package com.colofabrix.scala.beesight.detection.model

import com.colofabrix.scala.beesight.config.*
import com.colofabrix.scala.beesight.detection.model.DetectionConfigActions.*
import com.colofabrix.scala.beesight.model.EventType
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class DetectionConfigActionsSpec extends AnyFreeSpec with Matchers {

  private val config = DetectionConfig.default

  "DetectionConfigActions" - {

    "getEventConfigs" - {

      "should return TakeoffConfig for Takeoff event" in {
        config.getEventConfigs(EventType.Takeoff) shouldBe config.takeoff
      }

      "should return FreefallConfig for Freefall event" in {
        config.getEventConfigs(EventType.Freefall) shouldBe config.freefall
      }

      "should return CanopyConfig for Canopy event" in {
        config.getEventConfigs(EventType.Canopy) shouldBe config.canopy
      }

      "should return LandingConfig for Landing event" in {
        config.getEventConfigs(EventType.Landing) shouldBe config.landing
      }

      "should return an EventConfigs for every EventType" in
      EventType.values.foreach { event =>
        noException should be thrownBy config.getEventConfigs(event)
      }

    }

    "getBackwindowSize" - {

      "should return maximum of backtrack and smoothing window sizes" in
      EventType.values.foreach { event =>
        val eventConfig = config.getEventConfigs(event)
        val expected    = Math.max(eventConfig.backtrackWindowSize.value, eventConfig.smoothingWindowSize.value)
        config.getBackwindowSize(event) shouldBe expected
      }

      "should return at least the smoothing window size" in
      EventType.values.foreach { event =>
        val eventConfig = config.getEventConfigs(event)
        config.getBackwindowSize(event) should be >= (eventConfig.smoothingWindowSize.value: Int)
      }

      "should return at least the backtrack window size" in
      EventType.values.foreach { event =>
        val eventConfig = config.getEventConfigs(event)
        config.getBackwindowSize(event) should be >= (eventConfig.backtrackWindowSize.value: Int)
      }

    }

    "eventConfigsAsList" - {

      "should return exactly 4 configs" in {
        config.eventConfigsAsList.size shouldBe 4
      }

      "should contain all event configs" in {
        val configs = config.eventConfigsAsList
        configs should contain(config.takeoff)
        configs should contain(config.freefall)
        configs should contain(config.canopy)
        configs should contain(config.landing)
      }

    }

  }

}
