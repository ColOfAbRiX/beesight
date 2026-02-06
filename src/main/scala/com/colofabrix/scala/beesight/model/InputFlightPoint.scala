package com.colofabrix.scala.beesight.model

import com.colofabrix.scala.beesight.model.formats.FlysightPoint
import com.colofabrix.scala.beesight.detection.model.DataPoint

/**
 * Input data point for flight stage detection.
 *
 * @param time The timestamp of the data point
 * @param altitude Altitude in meters above sea level
 * @param speed Velocity vector with north, east, and vertical components (m/s)
 * @param source The original source data point
 */
final case class InputFlightPoint[A](
  point: DataPoint,
  source: A,
)
