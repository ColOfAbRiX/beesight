package com.colofabrix.scala.beesight.readers

import com.colofabrix.scala.beesight.model.GeoVector
import java.time.Instant

/**
 * Reader for a point of flight data
 */
trait PointReader[A] {

  def time(point: A): Instant
  def altitude(point: A): Double
  def speed(point: A): GeoVector

}

object PointReader {

  def apply[A: PointReader]: PointReader[A] =
    summon

}

extension [A: PointReader](self: A) {

  def time: Instant =
    PointReader[A].time(self)

  def altitude: Double =
    PointReader[A].altitude(self)

  def speed: GeoVector =
    PointReader[A].speed(self)

}
