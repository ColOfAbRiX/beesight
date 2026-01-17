package com.colofabrix.scala.beesight.model

/**
 * Represents the phases of a skydiving jump
 */
enum FlightPhase {
  case BeforeTakeoff, Takeoff, Freefall, Canopy, Landing
}

/**
 * A point in the flight timeline representing a detected stage transition
 *
 * @param lineIndex The line/row index in the original data file
 * @param altitude The altitude at this point in meters above sea level
 */
final case class FlightStagePoint(
  lineIndex: Long,
  altitude: Double,
)

/**
 * Input data point for flight stage detection
 *
 * @param time The timestamp of the data point
 * @param altitude Altitude in meters above sea level
 * @param northSpeed Velocity north in m/s
 * @param eastSpeed Velocity east in m/s
 * @param verticalSpeed Velocity down in m/s (positive = descending)
 * @param source The original source data point
 */
final case class InputFlightPoint[A](
  time: java.time.Instant,
  altitude: Double,
  northSpeed: Double,
  eastSpeed: Double,
  verticalSpeed: Double,
  source: A,
)

object InputFlightPoint {

  /**
   * Creates an InputFlightPoint from a FlysightPoint
   */
  def fromFlysightPoint(p: FlysightPoint): InputFlightPoint[FlysightPoint] =
    InputFlightPoint(
      time = p.time.toInstant,
      altitude = p.hMSL,
      northSpeed = p.velN,
      eastSpeed = p.velE,
      verticalSpeed = p.velD,
      source = p,
    )

}

/**
 * Output from streaming detection - enriched input point with detected flight stages
 *
 * @param phase The current detected flight phase for this point
 * @param takeoff The detected takeoff point (if found so far)
 * @param freefall The detected freefall/exit point (if found so far)
 * @param canopy The detected canopy deployment point (if found so far)
 * @param landing The detected landing point (if found so far)
 * @param lastPoint Index of the last processed data point
 * @param isValid True if this appears to be a valid skydiving jump (freefall detected)
 * @param source The original source data point
 */
final case class OutputFlightPoint[A](
  phase: FlightPhase,
  takeoff: Option[FlightStagePoint],
  freefall: Option[FlightStagePoint],
  canopy: Option[FlightStagePoint],
  landing: Option[FlightStagePoint],
  lastPoint: Long,
  isValid: Boolean,
  source: A,
)

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

  /**
   * Empty flight stages with no detected points
   */
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
