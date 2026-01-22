package com.colofabrix.scala.beesight.model

/**
 * Output from streaming detection - enriched input point with detected flight stages
 *
 * @param phase The current detected flight phase for this point
 * @param takeoff The detected takeoff point (if found so far)
 * @param freefall The detected freefall/exit point (if found so far)
 * @param canopy The detected canopy deployment point (if found so far)
 * @param landing The detected landing point (if found so far)
 * @param lastPoint Index of the last processed data point
 * @param source The original source data point
 */
final case class OutputFlightRow[A](
  phase: FlightPhase,
  takeoff: Option[FlightPoint],
  freefall: Option[FlightPoint],
  canopy: Option[FlightPoint],
  landing: Option[FlightPoint],
  lastPoint: Long,
  source: A,
)
