package com.colofabrix.scala.beesight.detection

import com.colofabrix.scala.beesight.config.FreefallConfig
import com.colofabrix.scala.beesight.detection.model.{ DetectedEvents, EventState, PointKinematics }

object FreefallDetection {

  def checkTrigger(
    state: EventState,
    kinematics: PointKinematics,
    previousSmoothedSpeed: Double,
    config: FreefallConfig,
  ): Boolean = {
    val smoothedVerticalSpeed = Smoothing.median(state.smoothingWindow)
    val acceleration          = Smoothing.computeAcceleration(smoothedVerticalSpeed, previousSmoothedSpeed, kinematics.deltaTime)

    val speedTriggered = smoothedVerticalSpeed > config.verticalSpeedThreshold

    val accelTriggered =
      acceleration > config.accelerationThreshold &&
      smoothedVerticalSpeed > config.accelerationMinVelocity

    speedTriggered || accelTriggered
  }

  def checkConstraints(
    events: DetectedEvents,
    kinematics: PointKinematics,
    index: Long,
    config: FreefallConfig,
  ): Boolean = {
    val notAlreadyDetected = events.freefall.isEmpty

    val afterTakeoff =
      events.takeoff.getOrTrue { takeoffPoint =>
        index > takeoffPoint.index
      }

    val aboveMinAltitude =
      events.takeoff match {
        case Some(takeoffPoint) =>
          kinematics.correctedAltitude > takeoffPoint.altitude + config.minAltitudeAbove ||
          kinematics.correctedAltitude > config.minAltitudeAbsolute
        case None =>
          kinematics.correctedAltitude > config.minAltitudeAbsolute
      }

    notAlreadyDetected && afterTakeoff && aboveMinAltitude
  }

  def checkValidation(state: EventState, config: FreefallConfig): Boolean = {
    val smoothedVerticalSpeed = Smoothing.median(state.smoothingWindow)
    smoothedVerticalSpeed > config.verticalSpeedThreshold * 0.8
  }

}
