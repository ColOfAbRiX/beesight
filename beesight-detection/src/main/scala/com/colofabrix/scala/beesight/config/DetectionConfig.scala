package com.colofabrix.scala.beesight.config

import io.github.iltotore.iron.*

/**
 * Top-level configuration for the flight stage detection algorithm.
 *
 * This is a pure data structure intrinsic to the detection engine. It carries no configuration-loading or CLI concerns;
 * those live in the CLI module (see `DetectionConfigCli`).
 *
 * @param global Settings shared across all stages
 * @param takeoff Thresholds and window sizes for takeoff detection
 * @param freefall Thresholds and window sizes for freefall/exit detection
 * @param canopy Thresholds and window sizes for canopy deployment detection
 * @param landing Thresholds and window sizes for landing detection
 * @param swoop Thresholds and settings for swoop metrics computation
 */
final case class DetectionConfig(
  global: GlobalConfig,
  takeoff: TakeoffConfig,
  freefall: FreefallConfig,
  canopy: CanopyConfig,
  landing: LandingConfig,
  swoop: SwoopConfig,
)

object DetectionConfig {

  /**
   * Default detection configuration, hardcoded as a pure value so the library has no configuration-loading dependency.
   */
  val default: DetectionConfig =
    DetectionConfig(
      global = GlobalConfig(
        preprocessWindowSize = WindowSize(20),
        accelerationClip = 20.0,
      ),
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
        validationVerticalSpeed = 0.05,
        horizontalSpeedThreshold = 2.0,
      ),
      swoop = SwoopConfig(
        minSwoopVerticalSpeed = 5.0,
        minHorizontalSpeed = 2.0,
        maxSwoopHeight = 300.0,
        recoveryFraction = 0.5,
        minSwoopDescent = 30.0,
        pullThreshold = 11.176,
        rolloutWindow = 30.48,
        sustainedRecoverySamples = 10,
      ),
    )

}

/**
 * Global preprocessing settings.
 * 
 * FIXME: These parameters are used only by the preprocessor, create a different case class (keep GlobalConfig too)
 *
 * @param preprocessWindowSize Size of the sliding window used for despiking; larger values smooth more aggressively
 * @param accelerationClip Maximum allowed velocity deviation (m/s) before a point is considered a spike
 */
final case class GlobalConfig(
  preprocessWindowSize: WindowSize,
  accelerationClip: Double,
)

/**
 * Common sliding-window parameters shared by all stage-specific detection configurations. Each stage defines these
 * three windows that together determine the geometry of the detection sliding window.
 */
sealed trait EventConfigs {

  /**
   * Number of recent points used for median-smoothing speed before kinematic computation.
   */
  def smoothingWindowSize: WindowSize

  /**
   * How far behind the focus point the detector searches for the trigger and inflection point.
   */
  def backtrackWindowSize: WindowSize

  /**
   * Number of forward-looking points after the trigger used to confirm the detection.
   */
  def validationWindowSize: WindowSize

}

/**
 * Configuration for takeoff detection.
 *
 * @param smoothingWindowSize Window size for median speed smoothing
 * @param backtrackWindowSize How far back to search for the takeoff trigger and inflection
 * @param validationWindowSize Forward-looking window for takeoff confirmation
 * @param validationVerticalSpeed Minimum sustained climb rate (m/s, negative = upward) to confirm takeoff
 * @param verticalSpeedThreshold Vertical speed (m/s) below which the trigger fires; negative means climbing
 * @param horizontalSpeedThreshold Minimum horizontal speed (m/s) required for the trigger
 * @param maxTakeoffAltitude Maximum altitude (m) at which takeoff can be detected
 */
final case class TakeoffConfig(
  smoothingWindowSize: WindowSize,
  backtrackWindowSize: WindowSize,
  validationWindowSize: WindowSize,
  validationVerticalSpeed: Double,
  verticalSpeedThreshold: Double,
  horizontalSpeedThreshold: Double,
  maxTakeoffAltitude: Double,
) extends EventConfigs

/**
 * Configuration for freefall/exit detection.
 *
 * @param smoothingWindowSize Window size for median speed smoothing
 * @param backtrackWindowSize How far back to search for the freefall trigger and inflection
 * @param validationWindowSize Forward-looking window for freefall confirmation
 * @param verticalSpeedThreshold Vertical descent speed (m/s, positive = downward) above which the primary trigger fires
 * @param accelerationThreshold Acceleration magnitude (m/s²) above which the secondary trigger fires
 * @param accelerationMinVelocity Minimum vertical speed (m/s) before the acceleration trigger applies
 * @param minFreefallAltitude Minimum altitude (m) at which freefall can be detected
 * @param validationVerticalSpeed Minimum sustained descent rate (m/s) to confirm freefall
 */
final case class FreefallConfig(
  smoothingWindowSize: WindowSize,
  backtrackWindowSize: WindowSize,
  validationWindowSize: WindowSize,
  verticalSpeedThreshold: Double,
  accelerationThreshold: Double,
  accelerationMinVelocity: Double,
  minFreefallAltitude: Double,
  validationVerticalSpeed: Double,
) extends EventConfigs

/**
 * Configuration for canopy deployment detection.
 *
 * @param smoothingWindowSize Window size for median speed smoothing
 * @param backtrackWindowSize How far back to search for the canopy trigger and inflection
 * @param validationWindowSize Forward-looking window for canopy confirmation
 * @param verticalSpeedThreshold Upper bound on vertical speed (m/s); must be slower than freefall
 * @param minCanopyAltitude Minimum altitude (m) at which canopy deployment can be detected
 * @param validationVerticalSpeed Minimum sustained descent rate (m/s) under canopy; cross-checked against freefall
 */
final case class CanopyConfig(
  smoothingWindowSize: WindowSize,
  backtrackWindowSize: WindowSize,
  validationWindowSize: WindowSize,
  verticalSpeedThreshold: Double,
  minCanopyAltitude: Double,
  validationVerticalSpeed: Double,
) extends EventConfigs

/**
 * Configuration for landing detection.
 *
 * @param smoothingWindowSize Window size for median speed smoothing
 * @param backtrackWindowSize How far back to search for the landing trigger
 * @param validationWindowSize Forward-looking window for landing stability confirmation
 * @param verticalSpeedThreshold Maximum absolute vertical speed (m/s); must be nearly stationary
 * @param stabilityThreshold Maximum std dev of vertical speed across the window for stability confirmation
 * @param validationVerticalSpeed Reserved for future use in landing validation
 * @param horizontalSpeedThreshold Maximum absolute horizontal speed (m/s); must be nearly stopped
 */
final case class LandingConfig(
  smoothingWindowSize: WindowSize,
  backtrackWindowSize: WindowSize,
  validationWindowSize: WindowSize,
  verticalSpeedThreshold: Double,
  stabilityThreshold: Double,
  validationVerticalSpeed: Double,
  horizontalSpeedThreshold: Double,
) extends EventConfigs

/**
 * Configuration for swoop metrics computation.
 *
 * @param minSwoopVerticalSpeed Minimum peak vertical speed (m/s) required to validate a swoop; below this, no swoop is reported
 * @param minHorizontalSpeed Minimum horizontal speed (m/s) below which heading is considered too noisy for rotation computation
 * @param maxSwoopHeight Maximum AGL height (m) at which a dive point can be detected; bounds the candidate peak search (gSwoop's 1000 ft cap), excluding high-altitude manoeuvres
 * @param recoveryFraction Fraction of the peak vertical speed above which a sustained re-ascent marks a manoeuvre recovery, disqualifying the candidate as dive point
 * @param minSwoopDescent Minimum descent (m) from the dive point to the ground; rejects flare-level speed bumps near touchdown
 * @param pullThreshold Descent rate (m/s) below which the dive is considered over; gSwoop's 25 mph walk-back threshold
 * @param rolloutWindow Height above the stop altitude (m) where the rollout search starts; gSwoop's 100 ft
 * @param sustainedRecoverySamples Consecutive samples above the recovery level that mark a manoeuvre recovery, disqualifying the candidate as dive point
 */
final case class SwoopConfig(
  minSwoopVerticalSpeed: Double,
  minHorizontalSpeed: Double,
  maxSwoopHeight: Double,
  recoveryFraction: Double,
  minSwoopDescent: Double,
  pullThreshold: Double,
  rolloutWindow: Double,
  sustainedRecoverySamples: Int,
)
