package com.colofabrix.scala.beesight.config

import java.time.Duration

/**
 * Configuration for the swoop detection pipeline and metrics computation.
 *
 * FIXME: This doesn't look like it's been grouped by functional areas. "altitudes", "speeds" and so on are not funtional area. They are determined by how the Swoop metrics work
 *
 * @param altitudes Altitude thresholds (m) of the detection stages
 * @param speeds    Speed thresholds (m/s) of the detection stages
 * @param angles    Heading thresholds (deg) of the detection stages
 * @param rotation  Thresholds of the rotation measurement
 * @param ground    Ground altitude estimation settings
 * @param stream    Input stream cutting settings
 */
final case class SwoopConfig(
  altitudes: SwoopAltitudesConfig,
  speeds: SwoopSpeedsConfig,
  angles: SwoopAnglesConfig,
  rotation: SwoopRotationConfig,
  ground: SwoopGroundConfig,
  stream: SwoopStreamConfig,
)

/**
 * Altitude thresholds (m) of the swoop detection stages.
 *
 * @param patternEntryAltitude Pattern entry: first crossing below this AGL altitude
 * @param diveAltitudeCap      Dive point: maximum AGL altitude at which the maximum-descent scan applies
 * @param maxAltitudeGain      Steepening walk: maximum altitude gain before the walk is aborted
 * @param bottomAltitudeOffset Bottom of turn: altitude offset above the bottom altitude for the backward bracket
 * @param rolloutWindow        Rollout start: height above the stop altitude where the rollout search starts
 */
final case class SwoopAltitudesConfig(
  patternEntryAltitude: Altitude,
  diveAltitudeCap: Altitude,
  maxAltitudeGain: Altitude,
  bottomAltitudeOffset: Altitude,
  rolloutWindow: Altitude,
)

/**
 * Speed thresholds (m/s) of the swoop detection stages.
 *
 * @param pullThreshold           Top of turn: descent rate below which the dive is considered over
 * @param steepeningEntry         Steepening walk: descent rate above which the accumulator is armed
 * @param steepeningAccumulator   Steepening walk: accumulated descent-rate increase that ends the walk
 * @param stopDescentThreshold    Stop search: descent rate below which a point can fix the swoop heading
 * @param stopHorizontalThreshold Stop search: horizontal speed below which a point can fix the swoop heading
 * @param stopEndHorizontal       Stop: horizontal speed below which the stop is confirmed
 * @param bottomDescentThreshold  Bottom candidate: descent rate above which the forward walk continues
 * @param bottomVzCross           Bottom bracket: descent rate whose forward crossing brackets the bottom of turn
 */
final case class SwoopSpeedsConfig(
  pullThreshold: Speed,
  steepeningEntry: Speed,
  steepeningAccumulator: Speed,
  stopDescentThreshold: Speed,
  stopHorizontalThreshold: Speed,
  stopEndHorizontal: Speed,
  bottomDescentThreshold: Speed,
  bottomVzCross: Speed,
)

/**
 * Heading thresholds (deg) of the swoop detection stages.
 *
 * @param stopHeadingDeviation Stop: heading deviation from the swoop heading that ends the search
 * @param maxTurnRate          Steepening walk: turn rate that aborts the walk
 */
final case class SwoopAnglesConfig(
  stopHeadingDeviation: Angle,
  maxTurnRate: TurnRate,
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
