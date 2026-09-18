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
  takeoff: Option[EventPoint],
  freefall: Option[EventPoint],
  canopy: Option[EventPoint],
  landing: Option[EventPoint],
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

    private[beesight] def setEventPoint(eventType: EventType, value: EventPoint): FlightEvents =
      eventType match {
        case EventType.Takeoff  => self.copy(takeoff = Some(value))
        case EventType.Freefall => self.copy(freefall = Some(value))
        case EventType.Canopy   => self.copy(canopy = Some(value))
        case EventType.Landing  => self.copy(landing = Some(value))
      }

    private[beesight] def phase: FlightPhase =
      if (self.landing.isDefined) FlightPhase.Landed
      else if (self.canopy.isDefined) FlightPhase.UnderCanopy
      else if (self.freefall.isDefined) FlightPhase.Freefall
      else if (self.takeoff.isDefined) FlightPhase.Climbing
      else FlightPhase.BeforeTakeoff

  }

}
