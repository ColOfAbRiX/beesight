package com.colofabrix.scala.beesight.detection.config

import java.time.Duration

/**
 * Configuration for the swoop detection pipeline and metrics computation, grouped by pipeline stage.
 *
 * @param patternEntry Pattern entry stage settings
 * @param dive         Dive point stage settings
 * @param topOfTurn    Top of turn stage settings
 * @param stop         Stop stage settings
 * @param rollout      Rollout stage settings
 * @param bottom       Bottom of turn stage settings
 * @param rotation     Thresholds of the rotation measurement
 * @param ground       Ground altitude estimation settings
 * @param stream       Input stream cutting settings
 */
final case class SwoopConfig(
  patternEntry: SwoopPatternEntryConfig,
  dive: SwoopDivePointConfig,
  topOfTurn: SwoopTopOfTurnConfig,
  stop: SwoopStopConfig,
  rollout: SwoopRolloutConfig,
  bottom: SwoopBottomOfTurnConfig,
  rotation: SwoopRotationConfig,
  ground: SwoopGroundConfig,
  stream: SwoopStreamConfig,
)

/**
 * Pattern entry stage settings.
 *
 * @param entryAltitude First crossing below this AGL altitude (m)
 */
final case class SwoopPatternEntryConfig(
  entryAltitude: Altitude,
)

/**
 * Dive point stage settings.
 *
 * @param altitudeCap Maximum AGL altitude (m) at which the maximum-descent scan applies
 */
final case class SwoopDivePointConfig(
  altitudeCap: Altitude,
)

/**
 * Top of turn stage settings: the walk-back from the dive point through the pull threshold, the deceleration boundary
 * and the steepening accumulator.
 *
 * @param pullThreshold         Descent rate (m/s) below which the dive is considered over
 * @param steepeningEntry       Descent rate (m/s) above which the accumulator is armed
 * @param steepeningAccumulator Accumulated descent-rate increase (m/s) that ends the walk
 * @param maxAltitudeGain       Maximum altitude gain (m) before the walk is aborted
 * @param maxTurnRate           Turn rate (deg/s) that aborts the walk
 */
final case class SwoopTopOfTurnConfig(
  pullThreshold: Speed,
  steepeningEntry: Speed,
  steepeningAccumulator: Speed,
  maxAltitudeGain: Altitude,
  maxTurnRate: TurnRate,
)

/**
 * Stop stage settings: the end of motion after the dive.
 *
 * @param descentThreshold    Descent rate (m/s) below which a point can fix the swoop heading
 * @param horizontalThreshold Horizontal speed (m/s) below which a point can fix the swoop heading
 * @param endHorizontal       Horizontal speed (m/s) below which the stop is confirmed
 * @param headingDeviation    Heading deviation (deg) from the swoop heading that ends the search
 */
final case class SwoopStopConfig(
  descentThreshold: Speed,
  horizontalThreshold: Speed,
  endHorizontal: Speed,
  headingDeviation: Angle,
)

/**
 * Rollout stage settings.
 *
 * @param window Height above the stop altitude (m) where the rollout search starts
 */
final case class SwoopRolloutConfig(
  window: Altitude,
)

/**
 * Bottom of turn stage settings.
 *
 * @param altitudeOffset   Altitude offset (m) above the bottom altitude for the backward bracket
 * @param descentThreshold Descent rate (m/s) above which the forward walk continues
 * @param vzCross          Descent rate (m/s) whose forward crossing brackets the bottom of turn
 */
final case class SwoopBottomOfTurnConfig(
  altitudeOffset: Altitude,
  descentThreshold: Speed,
  vzCross: Speed,
)

/**
 * Thresholds of the rotation measurement.
 *
 * @param fullTurnAngle          Per-sample turn angles (deg) at or above this value are skipped in the sum
 * @param diveAngleCap           Dive angle (deg) above which a point is inside a steep dive
 * @param diveVerticalSpeed      Descent rate (m/s) above which a point is inside a fast dive
 * @param turnStateThreshold     Per-sample turn angle (deg) above which a point turns right, below which it turns left
 * @param segmentAngleCap        Resolved segment heading change (deg) below which the segment angles are replaced
 * @param segmentDisplacementCap Maximum dead-reckoned displacement (m) of a segment from its start
 * @param trimEnabled            Whether the drift at the window ends is trimmed off the measurement
 * @param tailTrimThreshold      Per-sample turn angle (deg) at or below which a sample counts as drift when trimming the window tail
 * @param tailTrimCap            Net angle (deg) the tail trim may shift the measurement by at most
 * @param headTrimThreshold      Per-sample turn angle (deg) at or below which a sample counts as drift when trimming the window head
 * @param headTrimCap            Net angle (deg) the head trim may shift the measurement by at most
 */
final case class SwoopRotationConfig(
  fullTurnAngle: Angle,
  diveAngleCap: Angle,
  diveVerticalSpeed: Speed,
  turnStateThreshold: Angle,
  segmentAngleCap: Angle,
  segmentDisplacementCap: Altitude,
  trimEnabled: Boolean,
  tailTrimThreshold: Angle,
  tailTrimCap: Angle,
  headTrimThreshold: Angle,
  headTrimCap: Angle,
)

/**
 * Ground altitude estimation settings.
 *
 * @param window    Duration (s) of the window after landing used to estimate the ground altitude
 * @param slowSpeed Total speed (m/s) below which a point counts as slow in the ground fallback search
 */
final case class SwoopGroundConfig(
  window: Duration,
  slowSpeed: Speed,
)

/**
 * Input stream cutting settings.
 *
 * @param beforeCanopyMargin Duration (s) retained before the freefall event when cutting the stream for swoop detection
 */
final case class SwoopStreamConfig(
  beforeCanopyMargin: Duration,
)
