package com.colofabrix.scala.beesight.detection.math

import com.colofabrix.scala.beesight.collections.NonEmptyWindow.*
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.model.*
import java.time.Duration

/**
 * Computes kinematic properties (speed, acceleration) from flight data points.
 */
object Kinematics {

  /**
   * Computes acceleration by comparing velocity changes between two consecutive data points.
   */
  def compute(prev: IndexedDataPoint, curr: IndexedDataPoint): PointKinematics =
    val dt           = Duration.between(prev.time, curr.time).toMillis / 1000.0
    val acceleration = (curr.speed - prev.speed) / dt

    PointKinematics(
      time = curr.time,
      altitude = curr.altitude,
      speed = curr.speed,
      acceleration = acceleration,
    )

  def computeSmoothed(
    previousWindow: NonEmptyFilled[IndexedDataPoint],
    currentPoint: IndexedDataPoint,
  ): PointKinematics =
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
