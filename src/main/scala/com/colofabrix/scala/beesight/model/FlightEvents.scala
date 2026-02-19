package com.colofabrix.scala.beesight.model

/**
 * Represents the detected flight stage points from a jump file
 *
 * @param takeoff The detected takeoff point (if found)
 * @param freefall The detected freefall/exit point (if found)
 * @param canopy The detected canopy deployment point (if found)
 * @param landing The detected landing point (if found)
 */
final case class FlightEvents(
  takeoff: Option[FlightEventPoint],
  freefall: Option[FlightEventPoint],
  canopy: Option[FlightEventPoint],
  landing: Option[FlightEventPoint],
)

object FlightEvents {

  /**
   * Empty flight stages with no detected points
   */
  val empty: FlightEvents =
    FlightEvents(
      takeoff = None,
      freefall = None,
      canopy = None,
      landing = None,
    )

  extension (self: FlightEvents) {

    def setEvent(eventType: EventType, value: FlightEventPoint): FlightEvents =
      eventType match {
        case EventType.Takeoff  => self.copy(takeoff = Some(value))
        case EventType.Freefall => self.copy(freefall = Some(value))
        case EventType.Canopy   => self.copy(canopy = Some(value))
        case EventType.Landing  => self.copy(landing = Some(value))
      }

  }

}

/**
 * A point in the flight timeline representing a detected stage transition
 */
final case class FlightEventPoint(
  index: Long,
  altitude: Double,
)
