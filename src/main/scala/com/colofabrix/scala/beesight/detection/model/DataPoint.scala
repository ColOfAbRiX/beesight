package com.colofabrix.scala.beesight.detection.model

import com.colofabrix.scala.beesight.collections.SlidingWindow
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.model.InputFlightRow
import java.time.Instant

/**
 * Internal representation of a flight data point with time, altitude, and velocity.
 */
final private[detection] case class DataPoint(
  time: Instant,
  altitude: Double,
  speed: GeoVector,
)

private[detection] object DataPoint {

  def fromInputFlightRow[A](row: InputFlightRow[A]): (A, DataPoint) =
    (row.source, DataPoint(row.time, row.altitude, row.speed))

  extension (self: DataPoint)
    def toInputFlightRow[A](source: A): InputFlightRow[A] =
      InputFlightRow(self.time, self.altitude, self.speed, source)

}
