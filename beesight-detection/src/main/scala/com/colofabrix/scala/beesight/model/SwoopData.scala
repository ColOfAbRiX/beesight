package com.colofabrix.scala.beesight.model

import java.time.Duration

/**
 * Swoop metrics computed from the canopy-to-landing segment.
 *
 * @param turnInitiation     Where the speed-building maneuver began
 * @param rolloutStart       Where vertical speed peaked before final descent
 * @param maxVerticalSpeed   Highest vertical speed (m/s) during the turn phase
 * @param maxTotalSpeed      Highest 3D speed (m/s) during the turn phase
 * @param maxHorizontalSpeed Highest horizontal speed (m/s) during turn + rollout
 * @param degreesOfRotation  Cumulative heading change (degrees) during speed building
 * @param timeToExecuteTurn  Elapsed time from turn initiation to rollout start
 */
final private[beesight] case class SwoopData(
  turnInitiation: EventPoint,
  rolloutStart: EventPoint,
  maxVerticalSpeed: Double,
  maxTotalSpeed: Double,
  maxHorizontalSpeed: Double,
  degreesOfRotation: Double,
  timeToExecuteTurn: Duration,
)
