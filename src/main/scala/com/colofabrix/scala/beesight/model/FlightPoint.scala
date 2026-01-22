package com.colofabrix.scala.beesight.model

/**
 * A point in the flight timeline representing a detected stage transition
 */
final case class FlightPoint(
  index: Long,
  altitude: Double,
)
