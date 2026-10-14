package com.colofabrix.scala.beesight.detection.model

import com.colofabrix.scala.beesight.detection.config.*
import java.time.Duration

/**
 * Swoop metrics computed from the canopy-to-landing segment.
 *
 * All stage points carry AGL altitudes relative to [[ground]].
 *
 * @param turnInitiation     Where the speed-building maneuver began
 * @param rolloutStart       Where vertical speed peaked before final descent
 * @param maxVerticalSpeed   Highest vertical speed (m/s) during the turn phase, and the AGL altitude where it occurred
 * @param maxTotalSpeed      Highest 3D speed (m/s) during the turn phase, and the AGL altitude where it occurred
 * @param maxHorizontalSpeed Highest horizontal speed (m/s) during turn + rollout, and the AGL altitude where it occurred
 * @param degreesOfRotation  Cumulative signed heading change (degrees) during speed building: negative for left
 *                           turns, positive for right turns
 * @param turnDirection      Whether the speed-building turn goes left or right
 * @param timeToExecuteTurn  Elapsed time from turn initiation to rollout start
 * @param ground             Altitude of the detected ground (m MSL); stage point altitudes are relative to it
 * @param patternEntry       Where the flight first crossed below the pattern-entry altitude above the ground
 * @param divePoint          Where the maximum descent rate was reached below the dive altitude cap
 * @param stop               Where motion ended after the dive: the first point deviating from the swoop heading or
 *                           slowing below the end threshold
 * @param bottomOfTurn       The first point above the bottom altitude after the rollout
 */
final case class SwoopData(
  turnInitiation: EventPoint,
  rolloutStart: EventPoint,
  maxVerticalSpeed: SpeedAtAltitude,
  maxTotalSpeed: SpeedAtAltitude,
  maxHorizontalSpeed: SpeedAtAltitude,
  degreesOfRotation: Angle,
  turnDirection: TurnDirection,
  timeToExecuteTurn: Duration,
  ground: Altitude,
  patternEntry: EventPoint,
  divePoint: EventPoint,
  stop: EventPoint,
  bottomOfTurn: EventPoint,
)
