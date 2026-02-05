package com.colofabrix.scala.beesight.detection.math

import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.model.InputFlightRow
import java.time.Instant

object Interpolation {

  def interpolate(x1: Double, x2: Double, steps: Int, n: Int): Double =
    x1 + (x2 - x1) * (n.toDouble / steps)

  def interpolate(v1: GeoVector, v2: GeoVector, steps: Int, n: Int): GeoVector =
    v1 + (v2 - v1) * (n.toDouble / steps)

  def interpolate[A](p1: InputFlightRow[A], p2: InputFlightRow[A], time: Instant): InputFlightRow[A] = {
    val t1 = p1.time.toEpochMilli
    val t2 = p2.time.toEpochMilli
    val t  = (time.toEpochMilli - t1).toDouble / (t2 - t1)

    InputFlightRow(
      time = time,
      altitude = p1.altitude + (p2.altitude - p1.altitude) * t,
      northSpeed = p1.northSpeed + (p2.northSpeed - p1.northSpeed) * t,
      eastSpeed = p1.eastSpeed + (p2.eastSpeed - p1.eastSpeed) * t,
      verticalSpeed = p1.verticalSpeed + (p2.verticalSpeed - p1.verticalSpeed) * t,
      source = p1.source,
    )
  }

  def interpolate(k1: PointKinematics, k2: PointKinematics, time: Instant): PointKinematics = {
    val t1 = k1.time.toEpochMilli
    val t2 = k2.time.toEpochMilli
    val t  = (time.toEpochMilli - t1).toDouble / (t2 - t1)

    PointKinematics(
      time = time,
      altitude = k1.altitude + (k2.altitude - k1.altitude) * t,
      speed = k1.speed + (k2.speed - k1.speed) * t,
      acceleration = k1.acceleration + (k2.acceleration - k1.acceleration) * t,
    )
  }

}
