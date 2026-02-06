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
final case class OutputFlightPoint[A](
  takeoff: Option[FlightEventPoint],
  freefall: Option[FlightEventPoint],
  canopy: Option[FlightEventPoint],
  landing: Option[FlightEventPoint],
  source: A,
)

object OutputFlightPoint {

  extension [A](self: OutputFlightPoint[A]) {

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
