package com.colofabrix.scala.beesight.config

import com.colofabrix.scala.beesight.model.EventType

final case class GlobalConfig(
  preprocessWindowSize: Int,
  accelerationClip: Double,
  // UNUSED
  // inflectionMinSpeedDelta: Double,
)

sealed trait EventConfigs {
  def smoothingWindowSize: Int
  def backtrackWindowSize: Int
  def validationWindowSize: Int
}

final case class TakeoffConfig(
  smoothingWindowSize: Int,
  backtrackWindowSize: Int,
  validationWindowSize: Int,
  validationVerticalSpeed: Double,
  // UNUSED
  // speedThreshold: Double,
  // climbRate: Double,
  // maxAltitude: Double,
) extends EventConfigs

final case class FreefallConfig(
  smoothingWindowSize: Int,
  backtrackWindowSize: Int,
  validationWindowSize: Int,
  verticalSpeedThreshold: Double,
  accelerationThreshold: Double,
  accelerationMinVelocity: Double,
  minAltitudeAbove: Double,
  validationVerticalSpeed: Double,
) extends EventConfigs

final case class CanopyConfig(
  smoothingWindowSize: Int,
  backtrackWindowSize: Int,
  validationWindowSize: Int,
  verticalSpeedThreshold: Double,
  validationVerticalSpeed: Double,
) extends EventConfigs

final case class LandingConfig(
  smoothingWindowSize: Int,
  backtrackWindowSize: Int,
  validationWindowSize: Int,
  // UNUSED
  // speedMax: Double,
  // stabilityThreshold: Double,
  // meanVerticalSpeedMax: Double,
  // altitudeTolerance: Double,
  // stabilityWindowSize: Int,
) extends EventConfigs

final case class DetectionConfig(
  global: GlobalConfig,
  takeoff: TakeoffConfig,
  freefall: FreefallConfig,
  canopy: CanopyConfig,
  landing: LandingConfig,
)

object DetectionConfig {

  val default: DetectionConfig =
    DetectionConfig(
      global = GlobalConfig(
        preprocessWindowSize = 20,
        accelerationClip = 20.0,
        // inflectionMinSpeedDelta = 1.0,
      ),
      takeoff = TakeoffConfig(
        // speedThreshold = 25.0,
        // climbRate = -1.0,
        // maxAltitude = 600.0,
        smoothingWindowSize = 5,
        backtrackWindowSize = 10,
        validationWindowSize = 40,
        validationVerticalSpeed = -6.0
      ),
      freefall = FreefallConfig(
        verticalSpeedThreshold = 25.0,
        accelerationThreshold = 3.0,
        accelerationMinVelocity = 10.0,
        minAltitudeAbove = 600.0,
        validationVerticalSpeed = 7.0,
        smoothingWindowSize = 5,
        backtrackWindowSize = 10,
        validationWindowSize = 40,
      ),
      canopy = CanopyConfig(
        smoothingWindowSize = 5,
        backtrackWindowSize = 10,
        validationWindowSize = 40,
        verticalSpeedThreshold = 12.0,
        validationVerticalSpeed = 2.0
      ),
      landing = LandingConfig(
        // speedMax = 5.0,
        // stabilityThreshold = 0.5,
        // meanVerticalSpeedMax = 1.0,
        // altitudeTolerance = 500.0,
        // stabilityWindowSize = 10,
        smoothingWindowSize = 5,
        backtrackWindowSize = 10,
        validationWindowSize = 40,
      ),
    )

}
