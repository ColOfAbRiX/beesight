package com.colofabrix.scala.beesight.model

/**
 * A speed magnitude at a given altitude and stream position.
 */
final case class SpeedAtAltitude(
  idx: Long,
  speed: Double,
  altitude: Double,
)
