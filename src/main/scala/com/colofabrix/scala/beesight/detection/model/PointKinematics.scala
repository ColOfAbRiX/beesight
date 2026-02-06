package com.colofabrix.scala.beesight.detection.model

import java.time.Instant
import com.colofabrix.scala.beesight.model.GeoVector

/**
 * Represents complete kinematic state of a flight point including position, velocity, and acceleration.
 */
final case class PointKinematics(
  time: Instant,
  altitude: Double,
  speed: GeoVector,
  acceleration: GeoVector
)
