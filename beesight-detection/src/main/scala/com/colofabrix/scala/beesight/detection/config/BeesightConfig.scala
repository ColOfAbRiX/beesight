package com.colofabrix.scala.beesight.detection.config

import io.github.iltotore.iron.*
import java.time.Duration

/**
 * Top-level configuration for the flight stage detection algorithm.
 *
 * @param jumpevents Settings for the jump-events detection algorithm
 * @param swoop Thresholds and settings for swoop metrics computation
 * @param metrics Settings for the per-point flight metrics computation
 */
final case class BeesightConfig(
  jumpevents: JumpEventsConfig,
  swoop: SwoopConfig,
  metrics: MetricsConfig,
)

object BeesightConfig {

  /**
   * Default detection configuration
   */
  val default: BeesightConfig =
    BeesightConfig(
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
        altitudes = SwoopAltitudesConfig(
          patternEntryAltitude = Altitude(762.0),
          diveAltitudeCap = Altitude(304.8),
          maxAltitudeGain = Altitude(152.4),
          bottomAltitudeOffset = Altitude(1.0),
          rolloutWindow = Altitude(30.48),
        ),
        speeds = SwoopSpeedsConfig(
          pullThreshold = Speed(11.176),
          steepeningEntry = Speed(6.7056),
          steepeningAccumulator = Speed(6.7056),
          stopDescentThreshold = Speed(4.4704),
          stopHorizontalThreshold = Speed(8.9408),
          stopEndHorizontal = Speed(0.89408),
          bottomDescentThreshold = Speed(4.4704),
          bottomVzCross = Speed(1.25),
        ),
        angles = SwoopAnglesConfig(
          stopHeadingDeviation = Angle(45.0),
          maxTurnRate = TurnRate(30.0),
        ),
        rotation = SwoopRotationConfig(
          fullTurnAngle = Angle(360.0),
          diveAngleCap = Angle(60.0),
          diveVerticalSpeed = Speed(26.8224),
          turnStateThreshold = Angle(2.0),
          segmentAngleCap = Angle(315.0),
          segmentDisplacementCap = Altitude(9.144),
          trimEnabled = true,
          tailTrimThreshold = Angle(7.0),
          tailTrimCap = Angle(20.0),
          headTrimThreshold = Angle(2.0),
          headTrimCap = Angle(20.0),
        ),
        ground = SwoopGroundConfig(
          window = Duration.ofSeconds(10),
          slowSpeed = Speed(4.4704),
        ),
        stream = SwoopStreamConfig(
          beforeCanopyMargin = Duration.ofSeconds(10),
        ),
      ),
      metrics = MetricsConfig(
        slopeHalfWindow = 4,
        mass = Mass(90.0),
        planformArea = Area(2.0),
      ),
    )

}
