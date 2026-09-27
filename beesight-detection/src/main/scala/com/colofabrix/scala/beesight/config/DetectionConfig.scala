package com.colofabrix.scala.beesight.config

import io.github.iltotore.iron.*

/**
 * Top-level configuration for the flight stage detection algorithm.
 *
 * This is a pure data structure intrinsic to the detection engine. It carries no configuration-loading or CLI concerns;
 * those live in the CLI module (see `DetectionConfigCli`).
 *
 * @param jumpevents Settings for the jump-events detection algorithm
 * @param swoop Thresholds and settings for swoop metrics computation
 * @param metrics Settings for the per-point flight metrics computation
 */
final case class DetectionConfig(
  jumpevents: JumpEventsConfig,
  swoop: SwoopConfig,
  metrics: MetricsConfig,
)

object DetectionConfig {

  /**
   * Default detection configuration, hardcoded as a pure value so the library has no configuration-loading dependency.
   */
  val default: DetectionConfig =
    DetectionConfig(
      jumpevents = JumpEventsConfig(
        preprocessWindowSize = WindowSize(20),
        accelerationClip = Speed(20.0),
        takeoff = TakeoffConfig(
          smoothingWindowSize = WindowSize(5),
          backtrackWindowSize = WindowSize(10),
          validationWindowSize = WindowSize(100),
          validationVerticalSpeed = Speed(-2.466),
          verticalSpeedThreshold = Speed(-2.0),
          horizontalSpeedThreshold = Speed(30.0),
          maxTakeoffAltitude = Altitude(600.0),
        ),
        freefall = FreefallConfig(
          smoothingWindowSize = WindowSize(5),
          backtrackWindowSize = WindowSize(10),
          validationWindowSize = WindowSize(100),
          verticalSpeedThreshold = Speed(25.0),
          accelerationThreshold = Acceleration(3.0),
          accelerationMinVelocity = Speed(10.0),
          minFreefallAltitude = Altitude(600.0),
          validationVerticalSpeed = Speed(3.3565),
        ),
        canopy = CanopyConfig(
          smoothingWindowSize = WindowSize(5),
          backtrackWindowSize = WindowSize(10),
          validationWindowSize = WindowSize(100),
          verticalSpeedThreshold = Speed(10.0),
          minCanopyAltitude = Altitude(600.0),
          validationVerticalSpeed = Speed(1.3426),
        ),
        landing = LandingConfig(
          smoothingWindowSize = WindowSize(5),
          backtrackWindowSize = WindowSize(10),
          validationWindowSize = WindowSize(100),
          verticalSpeedThreshold = Speed(1.0),
          stabilityThreshold = Speed(0.5),
          horizontalSpeedThreshold = Speed(2.0),
        ),
      ),
      swoop = SwoopConfig(
        pullThreshold = Speed(11.176),
        rolloutWindow = Altitude(30.48),
      ),
      metrics = MetricsConfig(
        slopeHalfWindow = 4,
        mass = Mass(90.0),
        planformArea = Area(2.0),
      ),
    )

}
