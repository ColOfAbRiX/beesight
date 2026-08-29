package com.colofabrix.scala.beesight.model

import java.time.Instant

/**
 * Internal representation of a flight data point with time, altitude, and velocity.
 */
final private[beesight] case class DataPoint(
  time: Instant,
  altitude: Double,
  speed: GeoVector,
)

private[beesight] trait NormalizedPoint[A] {
  def time(point: A): Instant
  def altitude(point: A): Double
  def speed(point: A): GeoVector
}

object NormalizedPoint {

  def apply[A: NormalizedPoint]: NormalizedPoint[A] = summon

}

extension [A: NormalizedPoint](self: A) {
  def time: Instant    = NormalizedPoint[A].time(self)
  def altitude: Double = NormalizedPoint[A].altitude(self)
  def speed: GeoVector = NormalizedPoint[A].speed(self)
}
