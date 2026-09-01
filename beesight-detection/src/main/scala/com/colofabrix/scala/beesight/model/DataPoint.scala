package com.colofabrix.scala.beesight.model

import java.time.Instant

final case class DataPoint(
  time: Instant,
  altitude: Double,
  speed: GeoVector
)

object DataPoint {

  given PointReader[DataPoint] with {
    def time(point: DataPoint): Instant    = point.time
    def altitude(point: DataPoint): Double = point.altitude
    def speed(point: DataPoint): GeoVector = point.speed
  }

  def fromReader[A: PointReader](a: A): DataPoint =
    DataPoint(a.time, a.altitude, a.speed)
    
}
