package com.colofabrix.scala.beesight.model

import java.time.Instant

/**
 * Represents complete kinematic state of a flight point including position, velocity, and acceleration.
 */
final private[beesight] case class PointKinematics(
  point: IndexedPoint,
  acceleration: GeoVector,
)

private[beesight] object PointKinematics {

  given PointReader[PointKinematics] with {
    def time(data: PointKinematics): Instant    = data.point.time
    def altitude(data: PointKinematics): Double = data.point.altitude
    def speed(data: PointKinematics): GeoVector = data.point.speed
  }

}
