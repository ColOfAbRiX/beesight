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

}
