package com.colofabrix.scala.beesight.detection.model

import java.time.Instant
import com.colofabrix.scala.beesight.model.GeoVector

/**
 * Internal representation of a flight data point with time, altitude, and velocity.
 */
final case class IndexedDataPoint(
  time: Instant,
  idx: Long,
  altitude: Double,
  speed: GeoVector,
)
