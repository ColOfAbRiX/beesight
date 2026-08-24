package com.colofabrix.scala.beesight.model

import java.time.Instant

/**
 * Internal representation of a flight data point with time, altitude, and velocity.
 */
final case class DataPoint(
  time: Instant,
  altitude: Double,
  speed: GeoVector,
)
