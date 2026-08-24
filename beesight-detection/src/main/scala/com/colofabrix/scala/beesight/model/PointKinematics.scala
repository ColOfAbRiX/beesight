package com.colofabrix.scala.beesight.model

import java.time.Instant

/**
 * Represents complete kinematic state of a flight point including position, velocity, and acceleration.
 */
final private[beesight] case class PointKinematics(
  time: Instant,
  altitude: Double,
  speed: GeoVector,
  acceleration: GeoVector,
)
