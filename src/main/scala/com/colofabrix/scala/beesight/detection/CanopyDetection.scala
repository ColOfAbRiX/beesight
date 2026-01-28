package com.colofabrix.scala.beesight.detection

import com.colofabrix.scala.beesight.config.CanopyConfig
import com.colofabrix.scala.beesight.detection.model.{ DetectedEvents, EventState, PointKinematics }
import algebra.lattice.Bool

object CanopyDetection {

  def checkTrigger(state: EventState, config: CanopyConfig): Boolean = {
    val smoothedVerticalSpeed = Smoothing.median(state.smoothingWindow)
    smoothedVerticalSpeed > 0 &&
    smoothedVerticalSpeed < config.verticalSpeedMax
  }

  def checkConstraints(events: DetectedEvents, kinematics: PointKinematics, index: Long): Boolean = {
    val freefallDetected = events.freefall.isDefined

    val afterFreefall =
      events.freefall.getOrFalse { freefallPoint =>
        index > freefallPoint.index
      }

    val belowFreefallAltitude =
      events.freefall.getOrFalse { freefallPoint =>
        kinematics.correctedAltitude < freefallPoint.altitude
      }

    val aboveTakeoffAltitude =
      events.takeoff.getOrTrue { takeoffPoint =>
        kinematics.correctedAltitude > takeoffPoint.altitude
      }

    freefallDetected &&
    afterFreefall &&
    belowFreefallAltitude &&
    aboveTakeoffAltitude
  }

  def checkValidation(state: EventState, config: CanopyConfig): Boolean = {
    val smoothedVerticalSpeed = Smoothing.median(state.smoothingWindow)
    smoothedVerticalSpeed > 0 &&
    smoothedVerticalSpeed < config.verticalSpeedMax * 1.5
  }

}
