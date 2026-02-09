package com.colofabrix.scala.beesight.detection.stages

import com.colofabrix.scala.beesight.collections.NonEmptyWindow.NonEmptyFilled
import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.detection.math.Stats
import com.colofabrix.scala.beesight.detection.model.DetectionData

final class FreefallHandler private (config: DetectionConfig) {

  def detectTrigger(window: NonEmptyFilled[DetectionData]): Boolean =
    val smoothingWindow = window.leftInclusive.map(_.kinematics)
    val smoothedVerticalSpeed = Stats.median(smoothingWindow.toVector.map(_.speed.vertical))
    config.freefall.backtrackWindowSize > window.length

  // def checkTrigger(
  //   state: EventState,
  //   kinematics: PointKinematics,
  //   previousSmoothedSpeed: Double,
  //   config: FreefallConfig,
  // ): Boolean = {
  //   val smoothedVerticalSpeed = Smoothing.median(state.smoothingWindow)
  //   val acceleration          = Smoothing.computeAcceleration(smoothedVerticalSpeed, previousSmoothedSpeed, kinematics.deltaTime)

  //   val speedTriggered = smoothedVerticalSpeed > config.verticalSpeedThreshold

  //   val accelTriggered =
  //     acceleration > config.accelerationThreshold &&
  //     smoothedVerticalSpeed > config.accelerationMinVelocity

  //   speedTriggered || accelTriggered
  // }

}

object FreefallHandler {

  def apply(config: DetectionConfig): FreefallHandler =
    new FreefallHandler(config)

}
