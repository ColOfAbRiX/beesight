package com.colofabrix.scala.beesight.model

import java.time.Instant

/* FIXME: Move the types that in the package into the most specific package that uses them. For example DataPoint is
 * public and should stay in this package. But another case class might be used exclusively by jumpevents, so it should
 * go in com.colofabrix.scala.beesight.jumpevents.model and so on */

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
