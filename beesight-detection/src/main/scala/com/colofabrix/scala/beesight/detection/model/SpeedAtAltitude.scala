package com.colofabrix.scala.beesight.detection.model

import com.colofabrix.scala.beesight.detection.config.{ Altitude, Speed }

/**
 * A speed magnitude at a given altitude and stream position.
 */
final case class SpeedAtAltitude(
  idx: Long,
  speed: Speed,
  altitude: Altitude,
)
