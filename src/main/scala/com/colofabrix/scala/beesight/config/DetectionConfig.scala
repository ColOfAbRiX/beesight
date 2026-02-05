package com.colofabrix.scala.beesight.config

import com.colofabrix.scala.beesight.detection.model.EventType

final case class GlobalConfig(
  preprocessWindowSize: Int,
  accelerationClip: Double,
  // UNUSED
  inflectionMinSpeedDelta: Double,
)

final case class TakeoffConfig(
  smoothingWindowSize: Int,
  backtrackWindowSize: Int,
  validationWindowSize: Int,
  // UNUSED
  speedThreshold: Double,
  climbRate: Double,
  maxAltitude: Double,
)

final case class FreefallConfig(
  smoothingWindowSize: Int,
  backtrackWindowSize: Int,
  validationWindowSize: Int,
  // UNUSED
  verticalSpeedThreshold: Double,
  accelerationThreshold: Double,
  accelerationMinVelocity: Double,
  minAltitudeAbove: Double,
  minAltitudeAbsolute: Double,
)

final case class CanopyConfig(
  smoothingWindowSize: Int,
  backtrackWindowSize: Int,
  validationWindowSize: Int,
  // UNUSED
  verticalSpeedMax: Double,
)

final case class LandingConfig(
  smoothingWindowSize: Int,
  backtrackWindowSize: Int,
  validationWindowSize: Int,
  // UNUSED
  speedMax: Double,
  stabilityThreshold: Double,
  meanVerticalSpeedMax: Double,
  altitudeTolerance: Double,
  stabilityWindowSize: Int,
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
        preprocessWindowSize = 5,
        accelerationClip = 20.0,
        inflectionMinSpeedDelta = 1.0,
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
        speedMax = 5.0,
        stabilityThreshold = 0.5,
        meanVerticalSpeedMax = 1.0,
        altitudeTolerance = 500.0,
        stabilityWindowSize = 10,
        smoothingWindowSize = 5,
        backtrackWindowSize = 10,
        validationWindowSize = 40,
      ),
    )

  extension (self: DetectionConfig) {

    def getSmoothingWindowSize(eventType: EventType): Int =
      eventType match {
        case EventType.Takeoff  => self.takeoff.smoothingWindowSize
        case EventType.Freefall => self.freefall.smoothingWindowSize
        case EventType.Canopy   => self.canopy.smoothingWindowSize
        case EventType.Landing  => self.landing.smoothingWindowSize
      }

    def getValidationWindowSize(eventType: EventType): Int =
      eventType match {
        case EventType.Takeoff  => self.takeoff.validationWindowSize
        case EventType.Freefall => self.freefall.validationWindowSize
        case EventType.Canopy   => self.canopy.validationWindowSize
        case EventType.Landing  => self.landing.validationWindowSize
      }

    def getBacktrackWindowSize(eventType: EventType): Int =
      eventType match {
        case EventType.Takeoff  => self.takeoff.backtrackWindowSize
        case EventType.Freefall => self.freefall.backtrackWindowSize
        case EventType.Canopy   => self.canopy.backtrackWindowSize
        case EventType.Landing  => self.landing.backtrackWindowSize
      }

  }

}
