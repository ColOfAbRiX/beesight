package com.colofabrix.scala.beesight.detection

import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.model.*

/**
 * Detection logic for the takeoff phase.
 * Takeoff is detected when the aircraft starts moving horizontally (plane taking off).
 */
private[detection] object TakeoffDetection {

  /**
   * Result of takeoff detection for a single point.
   *
   * @param phase The detected flight phase after this point
   * @param point The takeoff point if detected during this transition
   */
  case class DetectionResult(
    phase: FlightPhase,
    point: Option[FlightPoint],
  )

  /**
   * Detect takeoff phase and record the takeoff point if conditions are met.
   * Note: Freefall takes priority over takeoff - if freefall conditions are met, skip takeoff.
   */
  def detect(state: StreamState[?], currentPoint: FlightPoint, config: DetectionConfig): DetectionResult =
    if state.detectedStages.takeoff.isDefined then
      DetectionResult(state.detectedPhase, state.detectedStages.takeoff)

    else if state.detectedPhase.ordinal > FlightPhase.Takeoff.ordinal then
      DetectionResult(state.detectedPhase, None)

    // Freefall takes priority over takeoff (as in original detectFlightPhase)
    else if FreefallDetection.isFreefallCondition(state.kinematics, config) then
      DetectionResult(state.detectedPhase, None)

    else if isTakeoffCondition(state.kinematics, config) then
      val shouldRecord =
        !state.takeoffMissing &&
        state.kinematics.altitude < config.TakeoffMaxAltitude

      val recordedPoint = if shouldRecord then Some(currentPoint) else None

      DetectionResult(FlightPhase.Takeoff, recordedPoint)

    else
      DetectionResult(state.detectedPhase, None)

  private def isTakeoffCondition(kinematics: Kinematics, config: DetectionConfig): Boolean =
    kinematics.horizontalSpeed > config.TakeoffSpeedThreshold &&
    kinematics.smoothedVerticalSpeed < config.TakeoffClimbRate

  def checkMissedTakeoff[A](
    prevState: StreamState[A],
    point: InputFlightRow[A],
    index: Long,
    config: DetectionConfig,
  ): Boolean =
    if index == 0 then
      point.altitude > config.TakeoffMaxAltitude && point.verticalSpeed < 0
    else
      prevState.takeoffMissing

}
