package com.colofabrix.scala.beesight.model

import java.time.Instant

trait PointReader[A] {
  def time(point: A): Instant
  def altitude(point: A): Double
  def speed(point: A): GeoVector
}

object PointReader {

  def apply[A: PointReader]: PointReader[A] = summon

}

extension [A: PointReader](self: A) {
  def time: Instant    = PointReader[A].time(self)
  def altitude: Double = PointReader[A].altitude(self)
  def speed: GeoVector = PointReader[A].speed(self)
}
