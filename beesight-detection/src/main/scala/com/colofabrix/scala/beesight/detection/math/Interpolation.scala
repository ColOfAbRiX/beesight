package com.colofabrix.scala.beesight.detection.math

import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.model.GeoVector
import java.time.Instant

/**
 * Provides linear interpolation functions for various flight data types.
 */
private[detection] object Interpolation {

  /**
   * Linearly interpolates between two Double values based on time.
   */
  def interpolate(x1: Double, x2: Double)(t1: Instant, t2: Instant, time: Instant): Double =
    interpolate_(x1, x2)(tParam(t1, t2, time))

  private def interpolate_(x1: Double, x2: Double)(t: Double): Double =
    x1 + (x2 - x1) * t

  /**
   * Linearly interpolates between two GeoVector values based on time.
   */
  def interpolate(v1: GeoVector, v2: GeoVector)(t1: Instant, t2: Instant, time: Instant): GeoVector =
    interpolate_(v1, v2)(tParam(t1, t2, time))

  private def interpolate_(v1: GeoVector, v2: GeoVector)(t: Double): GeoVector =
    GeoVector(
      north = interpolate_(v1.north, v2.north)(t),
      east = interpolate_(v1.east, v2.east)(t),
      vertical = interpolate_(v1.vertical, v2.vertical)(t),
    )

  /**
   * Linearly interpolates between two IndexedDataPoint values to a target time.
   */
  def interpolate[A](p1: IndexedDataPoint, p2: IndexedDataPoint, time: Instant): IndexedDataPoint =
    interpolate_(p1, p2, time)(tParam(p1.time, p2.time, time))

  private def interpolate_(p1: IndexedDataPoint, p2: IndexedDataPoint, time: Instant)(t: Double): IndexedDataPoint =
    IndexedDataPoint(
      time = time,
      idx = p1.idx,
      altitude = interpolate_(p1.altitude, p2.altitude)(t),
      speed = interpolate_(p1.speed, p2.speed)(t),
    )

  /**
   * Linearly interpolates between two PointKinematics values to a target time.
   */
  def interpolate(k1: PointKinematics, k2: PointKinematics, time: Instant): PointKinematics =
    interpolate_(k1, k2, time)(tParam(k1.time, k2.time, time))

  private def interpolate_(k1: PointKinematics, k2: PointKinematics, time: Instant)(t: Double): PointKinematics =
    PointKinematics(
      time = time,
      altitude = interpolate_(k1.altitude, k2.altitude)(t),
      speed = interpolate_(k1.speed, k2.speed)(t),
      acceleration = interpolate_(k1.acceleration, k2.acceleration)(t),
    )

  private def tParam(t1: Instant, t2: Instant, time: Instant): Double = (time.toEpochMilli - t1.toEpochMilli).toDouble /
    (t2.toEpochMilli - t1.toEpochMilli)

}
