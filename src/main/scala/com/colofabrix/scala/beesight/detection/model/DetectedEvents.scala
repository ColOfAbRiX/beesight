// package com.colofabrix.scala.beesight.detection.model

// import com.colofabrix.scala.beesight.model.*

// final case class DetectedEvents(
//   takeoff: Option[FlightEventPoint],
//   freefall: Option[FlightEventPoint],
//   canopy: Option[FlightEventPoint],
//   landing: Option[FlightEventPoint],
// )

// object DetectedEvents {

//   val empty: DetectedEvents =
//     DetectedEvents(None, None, None, None)

//   extension (self: DetectedEvents) {

//     def setDetectedEvents(eventType: EventType, point: Option[FlightEventPoint]): DetectedEvents =
//       eventType match {
//         case EventType.Takeoff  => self.copy(takeoff = point.orElse(self.takeoff))
//         case EventType.Freefall => self.copy(freefall = point.orElse(self.freefall))
//         case EventType.Canopy   => self.copy(canopy = point.orElse(self.canopy))
//         case EventType.Landing  => self.copy(landing = point.orElse(self.landing))
//       }

//   }

// }
