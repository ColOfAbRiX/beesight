package com.colofabrix.scala.beesight.detection

import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.model.*

/**
 * Orchestrates the detection of flight phases and recording of stage points.
 * Calls each detector in sequence: Takeoff → Freefall → Canopy → Landing
 */
private[detection] object PhaseDetection {

  /**
   * Result of phase detection for a single point.
   *
   * @param phase The final detected flight phase
   * @param stages The updated flight stages points
   */
  case class DetectionResult(
    phase: FlightPhase,
    stages: FlightEvents,
  )

  /**
   * Run all detectors in sequence to detect the current phase and record stage points.
   *
   * @param state Current stream state (contains previous phase, kinematics, detected stages)
   * @param currentPoint The current point as a FlightPoint
   * @param config Detection configuration
   * @return Detection result with updated phase and stages
   */
  def detectAll[A](
    state: StreamState[A],
    currentPoint: FlightPoint,
    config: DetectionConfig,
  ): DetectionResult =
    // Run takeoff detection
    val takeoffResult = TakeoffDetection.detect(state, currentPoint, config)
    val stagesAfterTakeoff = state.detectedStages.copy(
      takeoff = takeoffResult.point.orElse(state.detectedStages.takeoff),
    )
    val stateAfterTakeoff = state.copy(
      detectedPhase = takeoffResult.phase,
      detectedStages = stagesAfterTakeoff,
    )

    // Run freefall detection
    val freefallResult = FreefallDetection.detect(stateAfterTakeoff, currentPoint, config)
    val stagesAfterFreefall = stateAfterTakeoff.detectedStages.copy(
      freefall = freefallResult.point.orElse(stateAfterTakeoff.detectedStages.freefall),
    )
    val stateAfterFreefall = stateAfterTakeoff.copy(
      detectedPhase = freefallResult.phase,
      detectedStages = stagesAfterFreefall,
    )

    // Run canopy detection
    val canopyResult = CanopyDetection.detect(stateAfterFreefall, currentPoint, config)
    val stagesAfterCanopy = stateAfterFreefall.detectedStages.copy(
      canopy = canopyResult.point.orElse(stateAfterFreefall.detectedStages.canopy),
    )
    val stateAfterCanopy = stateAfterFreefall.copy(
      detectedPhase = canopyResult.phase,
      detectedStages = stagesAfterCanopy,
    )

    // Run landing detection
    val landingResult = LandingDetection.detect(stateAfterCanopy, currentPoint, config)

    // Build final result
    val finalStages = FlightEvents(
      takeoff = stagesAfterCanopy.takeoff,
      freefall = stagesAfterCanopy.freefall,
      canopy = stagesAfterCanopy.canopy,
      landing = landingResult.point.orElse(state.detectedStages.landing),
      lastPoint = state.dataPointIndex,
      isValid = stagesAfterCanopy.freefall.isDefined,
    )

    DetectionResult(
      phase = landingResult.phase,
      stages = finalStages,
    )

}
