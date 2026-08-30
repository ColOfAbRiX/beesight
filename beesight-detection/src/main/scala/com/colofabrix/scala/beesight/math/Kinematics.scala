package com.colofabrix.scala.beesight.math

import com.colofabrix.scala.beesight.collections.NonEmptyWindow.*
import com.colofabrix.scala.beesight.model.*
import java.time.Duration

/**
 * Computes kinematic properties (speed, acceleration) from flight data points.
 */
private[beesight] object Kinematics {

  /**
   * Computes acceleration by comparing velocity changes between two consecutive data points.
   */
  def compute[A: NormalizedPoint](prev: A, curr: IndexedDataPoint): PointKinematics =
    val dt           = Duration.between(prev.time, curr.time).toMillis / 1000.0
    val acceleration = (curr.speed - prev.speed) / dt

    PointKinematics(
      time = curr.time,
      altitude = curr.altitude,
      speed = curr.speed,
      acceleration = acceleration,
    )

  /**
   * Computes smoothed kinematics using median speed from a sliding window.
   */
  def computeSmoothed[A: NormalizedPoint](previousWindow: NonEmptyFilled[A], currentPoint: A): PointKinematics =
    val previousPoint = previousWindow.newest

    val currentWindow = previousWindow.enqueue(currentPoint)
    val currentSpeed  = Stats.medianBy(currentWindow)(_.speed)

    val dt                  = Duration.between(previousPoint.time, currentPoint.time).toMillis / 1000.0
    val currentAcceleration = (currentPoint.speed - previousPoint.speed) / dt

    PointKinematics(
      time = currentPoint.time,
      altitude = currentPoint.altitude,
      speed = currentSpeed,
      acceleration = currentAcceleration,
    )

}
