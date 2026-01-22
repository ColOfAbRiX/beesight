package com.colofabrix.scala.beesight.detection

import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.detection.model.*

/**
 * Detection logic for the canopy phase.
 */
private[detection] object CanopyDetection {

  /**
   * Detect canopy phase and record the deployment point if conditions are met.
   */
  def detect(state: StreamState[?], currentPoint: FlightPoint, config: DetectionConfig): Option[DetectionResult] =
    if state.detectedStages.canopy.isDefined then
      None
    else if state.detectedStages.freefall.isEmpty then
      None
    else if isCanopyCondition(state.kinematics, config) then
      val aboveTakeoff =
        state
          .detectedStages
          .takeoff
          .map(_.altitude)
          .forall(tAlt => state.kinematics.altitude > tAlt)

      val belowFreefall =
        state
          .detectedStages
          .freefall
          .map(_.altitude)
          .forall(fAlt => state.kinematics.altitude < fAlt)

      val afterFreefall =
        state
          .detectedStages
          .freefall
          .map(_.index)
          .forall(state.dataPointIndex > _)

      Option.when(aboveTakeoff && belowFreefall && afterFreefall)(buildResult(currentPoint))
    else
      None

  private def isCanopyCondition(kinematics: Kinematics, config: DetectionConfig): Boolean =
    kinematics.smoothedVerticalSpeed > 0 &&
    kinematics.smoothedVerticalSpeed < config.CanopyVerticalSpeedMax

  private def buildResult(point: FlightPoint): DetectionResult =
    DetectionResult(
      currentPhase = FlightPhase.Canopy,
      events = FlightEvents(
        takeoff = None,
        freefall = None,
        canopy = Some(point),
        landing = None,
        lastPoint = point.index,
        isValid = true,
      ),
      missedTakeoff = false,
    )

}
