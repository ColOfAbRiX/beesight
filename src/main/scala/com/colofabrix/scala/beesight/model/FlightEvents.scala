package com.colofabrix.scala.beesight.model

/**
 * Represents the detected flight stage points from a jump file
 *
 * @param takeoff The detected takeoff point (if found)
 * @param freefall The detected freefall/exit point (if found)
 * @param canopy The detected canopy deployment point (if found)
 * @param landing The detected landing point (if found)
 * @param lastPoint Index of the last processed data point
 * @param isValid True if this appears to be a valid skydiving jump (freefall detected)
 */
final case class FlightEvents(
  takeoff: Option[FlightPoint],
  freefall: Option[FlightPoint],
  canopy: Option[FlightPoint],
  landing: Option[FlightPoint],
  lastPoint: Long,
  isValid: Boolean,
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
      lastPoint = -1,
      isValid = false,
    )

}
