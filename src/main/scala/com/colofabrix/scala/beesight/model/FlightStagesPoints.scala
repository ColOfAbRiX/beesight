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
final case class FlightStagesPoints(
  takeoff: Option[FlightStagePoint],
  freefall: Option[FlightStagePoint],
  canopy: Option[FlightStagePoint],
  landing: Option[FlightStagePoint],
  lastPoint: Long,
  isValid: Boolean,
)

object FlightStagesPoints {

  val empty: FlightStagesPoints =
    FlightStagesPoints(
      takeoff = None,
      freefall = None,
      canopy = None,
      landing = None,
      lastPoint = -1,
      isValid = false,
    )

}

final case class FlightStagePoint(
  lineIndex: Long,
  altitude: Double,
)

enum FlightPhase {
  case Unknown, Takeoff, Freefall, Canopy, Landing
}

final case class InputFlightPoint[A](
  altitude: Double,
  northSpeed: Double,
  eastSpeed: Double,
  verticalSpeed: Double,
  source: A
)

final case class OutputFlightPoint[A](
  phase: FlightStagePoint,
  takeoff: Option[FlightStagePoint],
  freefall: Option[FlightStagePoint],
  canopy: Option[FlightStagePoint],
  landing: Option[FlightStagePoint],
  lastPoint: Long,
  isValid: Boolean,
  source: A
)
