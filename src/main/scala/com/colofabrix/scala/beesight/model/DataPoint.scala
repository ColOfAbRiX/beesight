package com.colofabrix.scala.beesight.detection.model

import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.model.GeoVector
import java.time.Instant

/**
 * Internal representation of a flight data point with time, altitude, and velocity.
 */
final private[beesight] case class DataPoint(
  time: Instant,
  altitude: Double,
  speed: GeoVector,
)
