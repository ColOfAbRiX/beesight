package com.colofabrix.scala.beesight.model

/**
 * Output data point enriched with detected flight stage information.
 *
 * @param takeoff The detected takeoff point (if found so far)
 * @param freefall The detected freefall/exit point (if found so far)
 * @param canopy The detected canopy deployment point (if found so far)
 * @param landing The detected landing point (if found so far)
 * @param source The original source data point from the input file
 */
final private[beesight] case class OutputFlightPoint[A](
  takeoff: Option[FlightEventPoint],
  freefall: Option[FlightEventPoint],
  canopy: Option[FlightEventPoint],
  landing: Option[FlightEventPoint],
  source: A,
)

/**
 * Extension methods for deriving the current flight phase from detected events.
 */
private[beesight] object OutputFlightPoint {

  extension [A](self: OutputFlightPoint[A]) {

    /**
     * Derives the current flight phase from whichever stage events have been detected so far.
     */
    def phase: FlightPhase =
      if (self.landing.isDefined) FlightPhase.Landed
      else if (self.canopy.isDefined) FlightPhase.UnderCanopy
      else if (self.freefall.isDefined) FlightPhase.Freefall
      else if (self.takeoff.isDefined) FlightPhase.Climbing
      else FlightPhase.BeforeTakeoff

  }

}
