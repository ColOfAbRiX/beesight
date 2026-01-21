package com.colofabrix.scala.beesight.detection

import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.model.*

/**
 * Detection logic for the freefall phase.
 * Freefall is detected when vertical speed exceeds threshold or acceleration indicates exit.
 */
private[detection] object FreefallDetection {

  /**
   * Result of freefall detection for a single point.
   *
   * @param phase The detected flight phase after this point
   * @param point The freefall/exit point if detected during this transition
   */
  case class DetectionResult(
    phase: FlightPhase,
    point: Option[FlightPoint],
  )

  /**
   * Detect freefall phase and record the exit point if conditions are met.
   *
   * @param state Current stream state (contains previous phase, kinematics, detected stages)
   * @param currentPoint The current point as a FlightPoint
   * @param config Detection configuration
   * @return Detection result with updated phase and possibly recorded point
   */
  def detect(state: StreamState[?], currentPoint: FlightPoint, config: DetectionConfig): DetectionResult =
    // If freefall already detected, preserve it
    if state.detectedStages.freefall.isDefined then
      DetectionResult(state.detectedPhase, state.detectedStages.freefall)
    // Can't detect freefall if we're past it
    else if state.detectedPhase.ordinal > FlightPhase.Freefall.ordinal then
      DetectionResult(state.detectedPhase, None)
    // Check if we're in freefall conditions
    else if isFreefallCondition(state.kinematics, config) then
      // Validate altitude constraints
      val altitudeValid = state.detectedStages.takeoff match {
        case Some(takeoff) => state.kinematics.altitude > takeoff.altitude + config.FreefallMinAltitudeAbove
        case None          => state.kinematics.altitude > config.FreefallMinAltitudeAbsolute
      }

      // Must be after takeoff (if detected)
      val afterTakeoff = state.detectedStages.takeoff
        .map(_.lineIndex)
        .forall(state.dataPointIndex > _)

      if altitudeValid && afterTakeoff then
        // Backtrack to find the true inflection point
        val inflectionPoint = findInflectionPoint(
          state.windows.backtrackVerticalSpeed.toVector,
          currentPoint,
          isRising = true,
        )
        DetectionResult(FlightPhase.Freefall, Some(inflectionPoint))
      else
        // Conditions met but validation failed - stay in freefall phase but don't record yet
        DetectionResult(FlightPhase.Freefall, None)
    else
      DetectionResult(state.detectedPhase, None)

  /**
   * Check if the current kinematics indicate freefall conditions.
   * Freefall when vertical speed exceeds threshold OR rapid acceleration detected.
   */
  private[detection] def isFreefallCondition(kinematics: Kinematics, config: DetectionConfig): Boolean =
    val byThreshold = kinematics.smoothedVerticalSpeed > config.FreefallVerticalSpeedThreshold

    val byAccel =
      kinematics.smoothedVerticalAcceleration > config.FreefallAccelThreshold &&
      kinematics.smoothedVerticalSpeed > config.FreefallAccelMinVelocity

    byThreshold || byAccel

  /**
   * Find the inflection point in the backtrack window where vertical speed started changing.
   *
   * @param history Recent vertical speed samples
   * @param detectedPoint The point where we detected the condition
   * @param isRising True if looking for rising inflection (freefall), false for falling (canopy/landing)
   * @return The inflection point or the detected point if none found
   */
  private[detection] def findInflectionPoint(
    history: Vector[VerticalSpeedSample],
    detectedPoint: FlightPoint,
    isRising: Boolean,
  ): FlightPoint =
    if history.isEmpty then detectedPoint
    else
      val candidate =
        history
          .sliding(2)
          .collect {
            case Vector(prev, curr) if isRising && curr.verticalSpeed > prev.verticalSpeed + 0.5  => prev
            case Vector(prev, curr) if !isRising && curr.verticalSpeed < prev.verticalSpeed - 0.5 => prev
          }
          .toList
          .headOption
          .getOrElse(if isRising then history.head else history.maxBy(_.verticalSpeed))

      FlightPoint(candidate.index, candidate.altitude)

}
