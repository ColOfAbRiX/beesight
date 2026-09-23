package com.colofabrix.scala.beesight.model

import com.colofabrix.scala.beesight.math.*
import com.colofabrix.scala.beesight.readers.*
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

  given Interpolation[PointKinematics] with
    def interpolate(k1: PointKinematics, k2: PointKinematics, time: Instant): PointKinematics =
      PointKinematics(
        point = Interpolation[IndexedPoint].interpolate(k1.point, k2.point, time),
        acceleration = Lerp[GeoVector].lerp(k1.acceleration, k2.acceleration, k1.point.time, k2.point.time, time),
      )

}
