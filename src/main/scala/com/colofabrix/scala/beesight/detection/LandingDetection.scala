package com.colofabrix.scala.beesight.detection

import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.model.*

/**
 * Detection logic for the landing phase.
 * Landing is detected when speed is low and vertical speed is stable.
 */
private[detection] object LandingDetection {

  /**
   * Result of landing detection for a single point.
   *
   * @param phase The detected flight phase after this point
   * @param point The landing point if detected during this transition
   */
  case class DetectionResult(
    phase: FlightPhase,
    point: Option[FlightPoint],
  )

  /**
   * Detect landing phase and record the landing point if conditions are met.
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
    // If landing already detected, preserve it
    if state.detectedStages.landing.isDefined then
      DetectionResult(state.detectedPhase, state.detectedStages.landing)
    // Can't detect landing if we're not under canopy
    else if state.detectedPhase != FlightPhase.Canopy then
      DetectionResult(state.detectedPhase, None)
    // Requires canopy to have been detected
    else if state.detectedStages.canopy.isEmpty then
      DetectionResult(state.detectedPhase, None)
    // Check if we're in landing conditions
    else if isLandingCondition(state, config) then
      // Altitude constraint: within ±LandingAltitudeTolerance of takeoff
      val altitudeOk = state.detectedStages.takeoff match {
        case Some(takeoff) => Math.abs(state.kinematics.altitude - takeoff.altitude) < config.LandingAltitudeTolerance
        case None          => true
      }

      // Must be below canopy altitude
      val belowCanopy = state.detectedStages.canopy
        .map(_.altitude)
        .forall(cAlt => state.kinematics.altitude < cAlt)

      // Must be after canopy point
      val afterCanopy = state.detectedStages.canopy
        .map(_.lineIndex)
        .forall(state.dataPointIndex > _)

      if altitudeOk && belowCanopy && afterCanopy then
        // Backtrack to find the true inflection point
        val inflectionPoint = FreefallDetection.findInflectionPoint(
          state.windows.backtrackVerticalSpeed.toVector,
          currentPoint,
          isRising = false,
        )
        DetectionResult(FlightPhase.Landing, Some(inflectionPoint))
      else
        DetectionResult(FlightPhase.Landing, None)
    else
      DetectionResult(state.detectedPhase, None)

  /**
   * Check if the current state indicates landing conditions.
   * Landing when total speed is low and vertical speed is stable.
   */
  private def isLandingCondition[A](state: StreamState[A], config: DetectionConfig): Boolean =
    lazy val windowStable = isWindowStable(
      state.windows.landingStability.toVector,
      config.LandingStabilityWindowSize,
      config,
    )
    state.kinematics.totalSpeed < config.LandingSpeedMax && windowStable

  /**
   * Check if the landing stability window indicates stable (near-zero) vertical speed.
   */
  private def isWindowStable(history: Vector[Double], windowSize: Int, config: DetectionConfig): Boolean =
    if history.size < windowSize then false
    else
      val mean     = history.sum / history.size
      val variance = history.map(v => Math.pow(v - mean, 2)).sum / history.size
      val stdDev   = Math.sqrt(variance)

      stdDev < config.LandingStabilityThreshold &&
      Math.abs(mean) < config.LandingMeanVerticalSpeedMax

}
