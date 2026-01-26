package com.colofabrix.scala.beesight.detection

import com.colofabrix.scala.beesight.config.TakeoffConfig
import com.colofabrix.scala.beesight.detection.model.{ DetectedEvents, EventState, PointKinematics }

object TakeoffDetection {

  def checkTrigger(state: EventState, kinematics: PointKinematics, config: TakeoffConfig): Boolean = {
    val smoothedVerticalSpeed = Smoothing.median(state.smoothingWindow)
    kinematics.horizontalSpeed > config.speedThreshold &&
    smoothedVerticalSpeed < config.climbRate
  }

  def checkConstraints(events: DetectedEvents, kinematics: PointKinematics, config: TakeoffConfig): Boolean = {
    val notAlreadyDetected = events.takeoff.isEmpty
    val belowMaxAltitude   = kinematics.correctedAltitude < config.maxAltitude
    notAlreadyDetected && belowMaxAltitude
  }

  def checkValidation(state: EventState, config: TakeoffConfig): Boolean = {
    val smoothedVerticalSpeed = Smoothing.median(state.smoothingWindow)
    smoothedVerticalSpeed < config.climbRate
  }

}
