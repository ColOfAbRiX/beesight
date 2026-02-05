package com.colofabrix.scala.beesight.model

import com.colofabrix.scala.beesight.detection.model.GeoVector
import com.colofabrix.scala.beesight.model.formats.FlysightPoint

/**
 * Input data point for flight stage detection.
 *
 * @param time The timestamp of the data point
 * @param altitude Altitude in meters above sea level
 * @param speed Velocity vector with north, east, and vertical components (m/s)
 * @param source The original source data point
 */
final case class InputFlightRow[A](
  time: java.time.Instant,
  altitude: Double,
  speed: GeoVector,
  source: A,
)
