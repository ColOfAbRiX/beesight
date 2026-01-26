package com.colofabrix.scala.beesight.config

final case class GlobalConfig(
  accelerationClip: Double,
)

final case class TakeoffConfig(
  speedThreshold: Double,
  climbRate: Double,
  maxAltitude: Double,
  smoothingWindowSize: Int,
  backtrackWindowSize: Int,
  validationWindowSize: Int,
)

final case class FreefallConfig(
  verticalSpeedThreshold: Double,
  accelerationThreshold: Double,
  accelerationMinVelocity: Double,
  minAltitudeAbove: Double,
  minAltitudeAbsolute: Double,
  smoothingWindowSize: Int,
  backtrackWindowSize: Int,
  validationWindowSize: Int,
)

final case class CanopyConfig(
  verticalSpeedMax: Double,
  smoothingWindowSize: Int,
  backtrackWindowSize: Int,
  validationWindowSize: Int,
)

final case class LandingConfig(
  speedMax: Double,
  stabilityThreshold: Double,
  meanVerticalSpeedMax: Double,
  altitudeTolerance: Double,
  stabilityWindowSize: Int,
  smoothingWindowSize: Int,
  backtrackWindowSize: Int,
  validationWindowSize: Int,
)

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
        accelerationClip = 20.0,
      ),
      takeoff = TakeoffConfig(
        speedThreshold = 25.0,
        climbRate = -1.0,
        maxAltitude = 600.0,
        smoothingWindowSize = 5,
        backtrackWindowSize = 10,
        validationWindowSize = 40,
      ),
      freefall = FreefallConfig(
        verticalSpeedThreshold = 25.0,
        accelerationThreshold = 3.0,
        accelerationMinVelocity = 10.0,
        minAltitudeAbove = 600.0,
        minAltitudeAbsolute = 600.0,
        smoothingWindowSize = 5,
        backtrackWindowSize = 10,
        validationWindowSize = 40,
      ),
      canopy = CanopyConfig(
        verticalSpeedMax = 12.0,
        smoothingWindowSize = 5,
        backtrackWindowSize = 10,
        validationWindowSize = 40,
      ),
      landing = LandingConfig(
        speedMax = 3.0,
        stabilityThreshold = 0.5,
        meanVerticalSpeedMax = 1.0,
        altitudeTolerance = 500.0,
        stabilityWindowSize = 10,
        smoothingWindowSize = 5,
        backtrackWindowSize = 10,
        validationWindowSize = 40,
      ),
    )

}
