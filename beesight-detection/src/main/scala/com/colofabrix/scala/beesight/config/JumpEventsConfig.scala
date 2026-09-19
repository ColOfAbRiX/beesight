package com.colofabrix.scala.beesight.config

/**
 * Configuration for the jump-events detection algorithm.
 *
 * @param preprocess Settings for the despiking stage
 * @param takeoff Thresholds and window sizes for takeoff detection
 * @param freefall Thresholds and window sizes for freefall/exit detection
 * @param canopy Thresholds and window sizes for canopy deployment detection
 * @param landing Thresholds and window sizes for landing detection
 */
final case class JumpEventsConfig(
  preprocess: PreprocessConfig,
  takeoff: TakeoffConfig,
  freefall: FreefallConfig,
  canopy: CanopyConfig,
  landing: LandingConfig,
)

/**
 * Configuration for the preprocessing stage.
 *
 * FIXME: Remover PreprocessConfig and move its memebers inside JumpEventsConfig.
 *
 * @param preprocessWindowSize Size of the sliding window used for despiking; larger values smooth more aggressively
 * @param accelerationClip Maximum allowed velocity deviation (m/s) before a point is considered a spike
 */
final case class PreprocessConfig(
  preprocessWindowSize: WindowSize,
  accelerationClip: Double,
)

/**
 * Common sliding-window parameters shared by all stage-specific detection configurations. Each stage defines these
 * three windows that together determine the geometry of the detection sliding window.
 */
sealed trait EventConfigs {

  /** Number of recent points used for median-smoothing speed before kinematic computation. */
  def smoothingWindowSize: WindowSize

  /** How far behind the focus point the detector searches for the trigger and inflection point. */
  def backtrackWindowSize: WindowSize

  /** Number of forward-looking points after the trigger used to confirm the detection. */
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
