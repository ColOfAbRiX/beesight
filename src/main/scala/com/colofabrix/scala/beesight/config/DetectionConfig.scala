package com.colofabrix.scala.beesight.config

final case class GlobalConfig(
  accelerationClip: Double,
  inflectionMinSpeedDelta: Double,
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
  import com.colofabrix.scala.beesight.detection.model.EventType

  val default: DetectionConfig =
    DetectionConfig(
      global = GlobalConfig(
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
