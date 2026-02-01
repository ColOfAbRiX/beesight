package com.colofabrix.scala.beesight.model

/**
 * Output from streaming detection - enriched input point with detected flight stages
 *
 * @param phase The current detected flight phase for this point
 * @param takeoff The detected takeoff point (if found so far)
 * @param freefall The detected freefall/exit point (if found so far)
 * @param canopy The detected canopy deployment point (if found so far)
 * @param landing The detected landing point (if found so far)
 * @param source The original source data point
 */
final case class OutputFlightRow[A](
  takeoff: Option[FlightPoint],
  freefall: Option[FlightPoint],
  canopy: Option[FlightPoint],
  landing: Option[FlightPoint],
  source: A,
)

object OutputFlightRow {

  extension [A](self: OutputFlightRow[A]) {

    def phase: FlightPhase =
      if (self.landing.isDefined) FlightPhase.Landed
      else if (self.canopy.isDefined) FlightPhase.UnderCanopy
      else if (self.freefall.isDefined) FlightPhase.Freefall
      else if (self.takeoff.isDefined) FlightPhase.Climbing
      else FlightPhase.BeforeTakeoff

    def phaseTransitionIndex: Long =
      phase match {
        case FlightPhase.BeforeTakeoff => 0
        case FlightPhase.Climbing      => self.takeoff.get.index
        case FlightPhase.Freefall      => self.freefall.get.index
        case FlightPhase.UnderCanopy   => self.canopy.get.index
        case FlightPhase.Landed        => self.landing.get.index
      }

  }

}
