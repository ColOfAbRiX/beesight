package com.colofabrix.scala.beesight.detection

import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.model.*

/**
 * Detection logic for the canopy phase.
 * Canopy deployment is detected when vertical speed drops below threshold after freefall.
 */
private[detection] object CanopyDetection {

  /**
   * Result of canopy detection for a single point.
   *
   * @param phase The detected flight phase after this point
   * @param point The canopy deployment point if detected during this transition
   */
  case class DetectionResult(
    phase: FlightPhase,
    point: Option[FlightPoint],
  )

  /**
   * Detect canopy phase and record the deployment point if conditions are met.
   *
   * @param state Current stream state (contains previous phase, kinematics, detected stages)
   * @param currentPoint The current point as a FlightPoint
   * @param config Detection configuration
   * @return Detection result with updated phase and possibly recorded point
   */
  def detect[A](
    state: StreamState[A],
    currentPoint: FlightPoint,
    config: DetectionConfig,
  ): DetectionResult =
    // If canopy already detected, preserve it
    if state.detectedStages.canopy.isDefined then
      DetectionResult(state.detectedPhase, state.detectedStages.canopy)
    // Can't detect canopy if we're not in freefall or past canopy phase
    else if state.detectedPhase != FlightPhase.Freefall then
      DetectionResult(state.detectedPhase, None)
    // Requires freefall to have been detected
    else if state.detectedStages.freefall.isEmpty then
      DetectionResult(state.detectedPhase, None)
    // Check if we're in canopy conditions
    else if isCanopyCondition(state.kinematics, config) then
      // Must be above takeoff altitude
      val aboveTakeoff = state.detectedStages.takeoff
        .map(_.altitude)
        .forall(tAlt => state.kinematics.altitude > tAlt)

      // Must be below freefall altitude
      val belowFreefall = state.detectedStages.freefall
        .map(_.altitude)
        .forall(fAlt => state.kinematics.altitude < fAlt)

      // Must be after freefall point
      val afterFreefall = state.detectedStages.freefall
        .map(_.lineIndex)
        .forall(state.dataPointIndex > _)

      if aboveTakeoff && belowFreefall && afterFreefall then
        // Backtrack to find the true inflection point (where speed started dropping)
        val inflectionPoint = FreefallDetection.findInflectionPoint(
          state.windows.backtrackVerticalSpeed.toVector,
          currentPoint,
          isRising = false,
        )
        DetectionResult(FlightPhase.Canopy, Some(inflectionPoint))
      else
        DetectionResult(FlightPhase.Canopy, None)
    else
      DetectionResult(state.detectedPhase, None)

  /**
   * Check if the current kinematics indicate canopy conditions.
   * Canopy when vertical speed is positive (descending) but below threshold.
   */
  private def isCanopyCondition(kinematics: Kinematics, config: DetectionConfig): Boolean =
    kinematics.smoothedVerticalSpeed > 0 &&
      kinematics.smoothedVerticalSpeed < config.CanopyVerticalSpeedMax

}
