package com.colofabrix.scala.beesight.detection

import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.model.*

/**
 * Detection logic for the landing phase.
 */
private[detection] object LandingDetection {

  /**
   * Detect landing phase and record the landing point if conditions are met.
   */
  def detect(state: StreamState[?], currentPoint: FlightPoint, config: DetectionConfig): Option[DetectionResult] =
    val detectLanding =
      !state.detectedStages.landing.isDefined &&
      !state.detectedStages.canopy.isEmpty &&
      isLandingCondition(state, config)

    lazy val altitudeOk =
      state.detectedStages.takeoff match {
        case Some(takeoff) => Math.abs(state.kinematics.altitude - takeoff.altitude) < config.LandingAltitudeTolerance
        case None          => true
      }

    lazy val belowCanopy =
      state
        .detectedStages
        .canopy
        .map(_.altitude)
        .forall(cAlt => state.kinematics.altitude < cAlt)

    lazy val afterCanopy =
      state
        .detectedStages
        .canopy
        .map(_.index)
        .forall(state.dataPointIndex > _)

    lazy val shouldRecord = altitudeOk && belowCanopy && afterCanopy

    lazy val inflectionPoint =
      Calculations.findInflectionPoint(
        state.windows.backtrackVerticalSpeed.toVector,
        currentPoint,
        isRising = false,
      )

    Option.when(detectLanding && shouldRecord)(buildResult(inflectionPoint))

  private def isLandingCondition(state: StreamState[?], config: DetectionConfig): Boolean =
    lazy val windowStable =
      isWindowStable(
        state.windows.landingStability.toVector,
        config.LandingStabilityWindowSize,
        config,
      )

    state.kinematics.totalSpeed < config.LandingSpeedMax &&
    windowStable

  private def isWindowStable(history: Vector[Double], windowSize: Int, config: DetectionConfig): Boolean =
    if history.size < windowSize then false
    else
      val mean     = history.sum / history.size
      val variance = history.map(v => Math.pow(v - mean, 2)).sum / history.size
      val stdDev   = Math.sqrt(variance)

      stdDev < config.LandingStabilityThreshold &&
      Math.abs(mean) < config.LandingMeanVerticalSpeedMax

  private def buildResult(point: FlightPoint): DetectionResult =
    DetectionResult(
      currentPhase = FlightPhase.Landing,
      events = FlightEvents(
        takeoff = None,
        freefall = None,
        canopy = None,
        landing = Some(point),
        lastPoint = point.index,
      ),
      missedTakeoff = false,
    )

}
