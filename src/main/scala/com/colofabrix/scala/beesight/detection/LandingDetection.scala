package com.colofabrix.scala.beesight.detection

import com.colofabrix.scala.beesight.collections.FixedSizeQueue
import com.colofabrix.scala.beesight.config.LandingConfig
import com.colofabrix.scala.beesight.detection.model.{ DetectedEvents, EventState, PointKinematics }

object LandingDetection {

  def checkTrigger(state: EventState, kinematics: PointKinematics, config: LandingConfig): Boolean = {
    val lowSpeed = kinematics.totalSpeed < config.speedMax
    val isStable = checkWindowStability(state.stabilityWindow, config)
    lowSpeed && isStable
  }

  def checkConstraints(events: DetectedEvents, kinematics: PointKinematics, index: Long): Boolean = {
    val notAlreadyDetected = events.landing.isEmpty
    val hasPrerequisite    = events.canopy.isDefined || events.takeoff.isDefined

    val afterCanopy =
      events.canopy.getOrTrue { canopyPoint =>
        index > canopyPoint.index
      }

    val belowCanopyAltitude =
      events.canopy.getOrTrue { canopyPoint =>
        kinematics.correctedAltitude < canopyPoint.altitude
      }

    notAlreadyDetected && hasPrerequisite && afterCanopy && belowCanopyAltitude
  }

  def checkValidation(state: EventState, kinematics: PointKinematics, config: LandingConfig): Boolean =
    kinematics.totalSpeed < config.speedMax * 2.0 &&
    checkWindowStability(state.stabilityWindow, config)

  private def checkWindowStability(window: FixedSizeQueue[Double], config: LandingConfig): Boolean =
    if (!window.isFull)
      false
    else {
      val mean   = Smoothing.mean(window)
      val stdDev = Smoothing.stdDev(window)

      stdDev < config.stabilityThreshold &&
      math.abs(mean) < config.meanVerticalSpeedMax
    }

}
