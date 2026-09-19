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
 */
final case class DetectionConfig(
  jumpevents: JumpEventsConfig,
  swoop: SwoopConfig,
)

object DetectionConfig {

  /**
   * Default detection configuration, hardcoded as a pure value so the library has no configuration-loading dependency.
   */
  val default: DetectionConfig =
    DetectionConfig(
      jumpevents = JumpEventsConfig(
        preprocessWindowSize = WindowSize(20),
        accelerationClip = 20.0,
        takeoff = TakeoffConfig(
          smoothingWindowSize = WindowSize(5),
          backtrackWindowSize = WindowSize(10),
          validationWindowSize = WindowSize(100),
          validationVerticalSpeed = -2.466,
          verticalSpeedThreshold = -2.0,
          horizontalSpeedThreshold = 30.0,
          maxTakeoffAltitude = 600.0,
        ),
        freefall = FreefallConfig(
          smoothingWindowSize = WindowSize(5),
          backtrackWindowSize = WindowSize(10),
          validationWindowSize = WindowSize(100),
          verticalSpeedThreshold = 25.0,
          accelerationThreshold = 3.0,
          accelerationMinVelocity = 10.0,
          minFreefallAltitude = 600.0,
          validationVerticalSpeed = 3.3565,
        ),
        canopy = CanopyConfig(
          smoothingWindowSize = WindowSize(5),
          backtrackWindowSize = WindowSize(10),
          validationWindowSize = WindowSize(100),
          verticalSpeedThreshold = 10.0,
          minCanopyAltitude = 600.0,
          validationVerticalSpeed = 1.3426,
        ),
        landing = LandingConfig(
          smoothingWindowSize = WindowSize(5),
          backtrackWindowSize = WindowSize(10),
          validationWindowSize = WindowSize(100),
          verticalSpeedThreshold = 1.0,
          stabilityThreshold = 0.5,
          horizontalSpeedThreshold = 2.0,
        ),
      ),
      swoop = SwoopConfig(
        pullThreshold = 11.176,
        rolloutWindow = 30.48,
      ),
    )

}
