package com.colofabrix.scala.beesight.detection

import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.model.*

/**
 * Detection logic for the freefall phase.
 */
private[detection] object FreefallDetection {

  /**
   * Detect freefall phase and record the exit point if conditions are met.
   */
  def detect(state: StreamState[?], currentPoint: FlightPoint, config: DetectionConfig): Option[DetectionResult] =
    if state.detectedStages.freefall.isDefined then
      None
    else if isFreefallCondition(state.kinematics, config) then
      val altitudeValid =
        state.detectedStages.takeoff match {
          case Some(takeoff) => state.kinematics.altitude > takeoff.altitude + config.FreefallMinAltitudeAbove
          case None          => state.kinematics.altitude > config.FreefallMinAltitudeAbsolute
        }

      val afterTakeoff =
        state
          .detectedStages
          .takeoff
          .map(_.index)
          .forall(state.dataPointIndex > _)

      lazy val inflectionPoint =
        Calculations.findInflectionPoint(
          state.windows.backtrackVerticalSpeed.toVector,
          currentPoint,
          isRising = true,
        )

      Option.when(altitudeValid && afterTakeoff)(buildResult(inflectionPoint))
    else
      None

  private def isFreefallCondition(kinematics: Kinematics, config: DetectionConfig): Boolean =
    val byThreshold = kinematics.smoothedVerticalSpeed > config.FreefallVerticalSpeedThreshold

    val byAccel =
      kinematics.smoothedVerticalAcceleration > config.FreefallAccelThreshold &&
      kinematics.smoothedVerticalSpeed > config.FreefallAccelMinVelocity

    byThreshold || byAccel

  private def buildResult(point: FlightPoint): DetectionResult =
    DetectionResult(
      currentPhase = FlightPhase.Freefall,
      events = FlightEvents(
        takeoff = None,
        freefall = Some(point),
        canopy = None,
        landing = None,
        lastPoint = point.index,
        isValid = true,
      ),
      missedTakeoff = false,
    )

}
