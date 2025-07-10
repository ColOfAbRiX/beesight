package com.colofabrix.scala.beesight.model

final case class FlightStagesPoints(
  takeoff: Option[DataPoint],
  freefall: Option[DataPoint],
  canopy: Option[DataPoint],
  landing: Option[DataPoint],
  lastPoint: Long
)

object FlightStagesPoints {
  val empty: FlightStagesPoints = FlightStagesPoints(None, None, None, None, -1)
}

final case class DataPoint(
  lineIndex: Long,
  altitude: Option[Double],
)
