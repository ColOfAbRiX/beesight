package com.colofabrix.scala.beesight.math

import com.colofabrix.scala.beesight.model.*
import java.time.Duration

/**
 * Computes kinematic properties (speed, acceleration) from flight data points.
 */
private[beesight] object Kinematics {

  /**
   * Computes acceleration by comparing velocity changes between two consecutive data points.
   */
  def compute(prev: IndexedPoint, curr: IndexedPoint): PointKinematics =
    val dt           = Duration.between(prev.time, curr.time).toMillis / 1000.0
    val acceleration = (curr.speed - prev.speed) / dt

    PointKinematics(
      point = curr,
      acceleration = acceleration,
    )

}
