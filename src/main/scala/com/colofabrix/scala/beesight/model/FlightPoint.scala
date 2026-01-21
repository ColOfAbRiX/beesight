package com.colofabrix.scala.beesight.model

/**
 * A point in the flight timeline representing a detected stage transition
 *
 * @param lineIndex The line/row index in the original data file
 * @param altitude The altitude at this point in meters above sea level
 */
final case class FlightPoint(
  lineIndex: Long,
  altitude: Double,
)
