package com.colofabrix.scala.beesight.detection.model

import com.colofabrix.scala.beesight.model.FlightPoint

final case class DetectedEvents(
  takeoff: Option[FlightPoint],
  freefall: Option[FlightPoint],
  canopy: Option[FlightPoint],
  landing: Option[FlightPoint],
)

object DetectedEvents {

  val empty: DetectedEvents =
    DetectedEvents(None, None, None, None)

  extension (self: DetectedEvents) {

    def updateDetectedEvents(eventType: EventType, point: Option[FlightPoint]): DetectedEvents =
      eventType match {
        case EventType.Takeoff  => self.copy(takeoff = point.orElse(self.takeoff))
        case EventType.Freefall => self.copy(freefall = point.orElse(self.freefall))
        case EventType.Canopy   => self.copy(canopy = point.orElse(self.canopy))
        case EventType.Landing  => self.copy(landing = point.orElse(self.landing))
      }

  }

}
