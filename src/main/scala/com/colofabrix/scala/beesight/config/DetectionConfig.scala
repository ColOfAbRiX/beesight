package com.colofabrix.scala.beesight.config

import com.colofabrix.scala.beesight.model.EventType

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
      ),
      takeoff = TakeoffConfig(
        smoothingWindowSize = 5,
        backtrackWindowSize = 10,
        validationWindowSize = 40,
        validationVerticalSpeed = -4.0,
        verticalSpeedThreshold = -6.0,
        horizontalSpeedThreshold = 25.0,
        maxTakeoffAltitude = 600.0,
      ),
      freefall = FreefallConfig(
        verticalSpeedThreshold = 25.0,
        accelerationThreshold = 3.0,
        accelerationMinVelocity = 10.0,
        minFreefallAltitude = 600.0,
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
        minCanopyAltitude = 600.0,
        validationVerticalSpeed = 2.0,
      ),
      landing = LandingConfig(
        smoothingWindowSize = 5,
        backtrackWindowSize = 10,
        validationWindowSize = 40,
        verticalSpeedThreshold = 1.0,
        stabilityThreshold = 0.5,
        validationVerticalSpeed = 0.05,
        horizontalSpeedThreshold = 2.0
      ),
    )

}

final case class GlobalConfig(
  preprocessWindowSize: Int,
  accelerationClip: Double,
)

sealed trait EventConfigs {
  def smoothingWindowSize: Int
  def backtrackWindowSize: Int
  def validationWindowSize: Int
  def validationVerticalSpeed: Double
  def verticalSpeedThreshold: Double
}

final case class TakeoffConfig(
  smoothingWindowSize: Int,
  backtrackWindowSize: Int,
  validationWindowSize: Int,
  validationVerticalSpeed: Double,
  verticalSpeedThreshold: Double,
  horizontalSpeedThreshold: Double,
  maxTakeoffAltitude: Double,
) extends EventConfigs

final case class FreefallConfig(
  smoothingWindowSize: Int,
  backtrackWindowSize: Int,
  validationWindowSize: Int,
  verticalSpeedThreshold: Double,
  accelerationThreshold: Double,
  accelerationMinVelocity: Double,
  minFreefallAltitude: Double,
  validationVerticalSpeed: Double,
) extends EventConfigs

final case class CanopyConfig(
  smoothingWindowSize: Int,
  backtrackWindowSize: Int,
  validationWindowSize: Int,
  verticalSpeedThreshold: Double,
  minCanopyAltitude: Double,
  validationVerticalSpeed: Double,
) extends EventConfigs

final case class LandingConfig(
  smoothingWindowSize: Int,
  backtrackWindowSize: Int,
  validationWindowSize: Int,
  verticalSpeedThreshold: Double,
  stabilityThreshold: Double,
  validationVerticalSpeed: Double,
  horizontalSpeedThreshold: Double
) extends EventConfigs
