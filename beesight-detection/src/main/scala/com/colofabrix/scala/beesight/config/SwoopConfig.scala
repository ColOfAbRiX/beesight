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
 * @param carve     Thresholds of the carve turn-angle measurement
 * @param ground    Ground altitude estimation settings
 * @param stream    Input stream cutting settings
 */
final case class SwoopConfig(
  altitudes: SwoopAltitudesConfig,
  speeds: SwoopSpeedsConfig,
  angles: SwoopAnglesConfig,
  carve: SwoopCarveConfig,
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
 * Thresholds of the carve turn-angle measurement.
 *
 * @param rateThreshold      Smoothed turn rate (deg/s) a sample must exceed for the carve to exist at all
 * @param exitRateThreshold  Smoothed turn rate (deg/s) above which a sample keeps the carve expanding once it exists
 * @param maxTurnDegrees     Turn-angle size (deg) above which the hysteresis expansion is discarded for the plain entry-threshold carve
 * @param reliableSpeed      Minimum horizontal speed (m/s) for the GPS heading to be reliable
 * @param gapSamples         Maximum run of below-threshold samples tolerated inside the carve
 * @param directionSamples   Samples either side of the dive point establishing the turn direction
 * @param minDirectionChange Minimum net heading change (deg) around the dive point for a turn direction to be established
 */
final case class SwoopCarveConfig(
  rateThreshold: TurnRate,
  exitRateThreshold: TurnRate,
  maxTurnDegrees: Angle,
  reliableSpeed: Speed,
  gapSamples: WindowSize,
  directionSamples: WindowSize,
  minDirectionChange: Angle,
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
