package com.colofabrix.scala.beesight.detection.model

import com.colofabrix.scala.beesight.config.*
import com.colofabrix.scala.beesight.model.EventType

/**
 * Extension methods for looking up stage-specific configurations from DetectionConfig.
 */
private[detection] object DetectionConfigActions {

  extension (self: DetectionConfig) {

    def getEventConfigs(eventType: EventType): EventConfigs =
      eventType match {
        case EventType.Takeoff  => self.takeoff
        case EventType.Freefall => self.freefall
        case EventType.Canopy   => self.canopy
        case EventType.Landing  => self.landing
      }

    def getBackwindowSize(eventType: EventType): Int =
      val eventTypeConfig = getEventConfigs(eventType)
      Math.max(eventTypeConfig.backtrackWindowSize.value, eventTypeConfig.smoothingWindowSize.value)

    def eventConfigsAsList: Vector[EventConfigs] =
      Vector(self.takeoff, self.freefall, self.canopy, self.landing)

  }

}
