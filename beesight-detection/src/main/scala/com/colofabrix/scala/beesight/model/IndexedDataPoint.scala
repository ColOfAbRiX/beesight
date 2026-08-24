package com.colofabrix.scala.beesight.model

import java.time.Instant

/**
 * Internal representation of a flight data point with time, altitude, and velocity.
 */
final private[beesight] case class IndexedDataPoint(
  time: Instant,
  idx: Long,
  altitude: Double,
  speed: GeoVector,
)
