package com.colofabrix.scala.beesight.detection.model

import com.colofabrix.scala.beesight.collections.SlidingWindow
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.model.InputFlightRow
import java.time.Instant

final private[detection] case class DataPoint(
  time: Instant,
  altitude: Double,
  speed: GeoVector,
)

private[detection] object DataPoint {

  def fromInputFlightRow[A](row: InputFlightRow[A]): (A, DataPoint) =
    (row.source, DataPoint(row.time, row.altitude, GeoVector(row.northSpeed, row.eastSpeed, row.verticalSpeed)))

  extension (self: DataPoint)
    def toInputFlightRow[A](source: A): InputFlightRow[A] =
      InputFlightRow(self.time, self.altitude, self.speed.north, self.speed.east, self.speed.vertical, source)

}
