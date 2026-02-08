package com.colofabrix.scala.beesight.detection.math

import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.model.GeoVector
import java.time.Duration

/**
 * Computes kinematic properties (speed, acceleration) from flight data points.
 */
object Kinematics {

  /**
   * Computes acceleration by comparing velocity changes between two consecutive data points.
   */
  def compute(prev: DataPoint, curr: DataPoint): PointKinematics =
    val dt           = Duration.between(prev.time, curr.time).toMillis / 1000.0
    val acceleration = (curr.speed - prev.speed) / dt

    PointKinematics(
      time = curr.time,
      altitude = curr.altitude,
      speed = curr.speed,
      acceleration = acceleration,
    )

}
