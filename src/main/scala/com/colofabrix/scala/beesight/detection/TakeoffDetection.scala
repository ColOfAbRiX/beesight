package com.colofabrix.scala.beesight.detection

import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.model.*

/**
 * Detection logic for the takeoff phase.
 * Takeoff is detected when the aircraft starts moving horizontally (plane taking off).
 */
private[detection] object TakeoffDetection {

  /**
   * Detect takeoff phase and record the takeoff point if conditions are met.
   */
  def detect(state: StreamState[?], currentPoint: FlightPoint, config: DetectionConfig): Option[DetectionResult] =
    val detectTakeoff =
      !state.detectedStages.takeoff.isDefined &&
      isTakeoffCondition(state.kinematics, config)

    lazy val shouldRecord =
      !state.takeoffMissing &&
      state.kinematics.altitude < config.TakeoffMaxAltitude

    lazy val inflectionPoint =
      Calculations.findInflectionPoint(
        state.windows.backtrackVerticalSpeed.toVector,
        currentPoint,
        isRising = true,
      )

    Option.when(detectTakeoff && shouldRecord)(buildResult(inflectionPoint))

  private def isTakeoffCondition(kinematics: Kinematics, config: DetectionConfig): Boolean =
    kinematics.horizontalSpeed > config.TakeoffSpeedThreshold &&
    kinematics.smoothedVerticalSpeed < config.TakeoffClimbRate

  private def buildResult(point: FlightPoint): DetectionResult =
    DetectionResult(
      currentPhase = FlightPhase.Takeoff,
      events = FlightEvents(
        takeoff = Some(point),
        freefall = None,
        canopy = None,
        landing = None,
        lastPoint = point.index,
      ),
      missedTakeoff = false,
    )

}
