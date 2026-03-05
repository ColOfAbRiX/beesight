package com.colofabrix.scala.beesight.detection.model

import com.colofabrix.scala.beesight.model.GeoVector
import java.time.Instant

/**
 * Internal representation of a flight data point with time, altitude, and velocity.
 */
final case class IndexedDataPoint(
  time: Instant,
  idx: Long,
  altitude: Double,
  speed: GeoVector,
)
