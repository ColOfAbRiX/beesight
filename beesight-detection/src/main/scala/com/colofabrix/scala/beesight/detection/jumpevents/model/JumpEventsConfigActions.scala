package com.colofabrix.scala.beesight.detection.jumpevents.model

import com.colofabrix.scala.beesight.detection.config.*
import com.colofabrix.scala.beesight.detection.model.{ EventType, FlightPhase }

/**
 * Extension methods for looking up event-specific configurations from JumpEventsConfig.
 */
private[jumpevents] object JumpEventsConfigActions {

  extension (self: JumpEventsConfig) {

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

    def smoothingWindowFor(phase: FlightPhase): WindowSize =
      phase match {
        case FlightPhase.BeforeTakeoff => self.takeoff.smoothingWindowSize
        case FlightPhase.Climbing      => self.freefall.smoothingWindowSize
        case FlightPhase.Freefall      => self.canopy.smoothingWindowSize
        case FlightPhase.UnderCanopy   => self.landing.smoothingWindowSize
        case FlightPhase.Landed        => self.landing.smoothingWindowSize
      }

    def eventConfigsAsList: Vector[EventConfigs] =
      Vector(
        self.takeoff,
        self.freefall,
        self.canopy,
        self.landing
      )

  }

}
