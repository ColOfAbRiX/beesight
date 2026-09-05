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
  def compute[A: PointReader](prev: A, curr: A): PointKinematics =
    val dt           = Duration.between(prev.time, curr.time).toMillis / 1000.0
    val acceleration = (curr.speed - prev.speed) / dt

    PointKinematics(
      point = DataPoint.fromReader(curr),
      acceleration = acceleration,
    )

  // /**
  //  * Computes smoothed kinematics using median speed from a sliding window.
  //  */
  // def computeSmoothed[A: PointReader](previousWindow: NonEmptyFilled[A], currentPoint: A): PointKinematics =
  //   val previousPoint = previousWindow.newest

  //   val currentWindow = previousWindow.enqueue(currentPoint)
  //   val currentSpeed  = Stats.medianBy(currentWindow)(_.speed)

  //   val dt                  = Duration.between(previousPoint.time, currentPoint.time).toMillis / 1000.0
  //   val currentAcceleration = (currentPoint.speed - previousPoint.speed) / dt

  //   PointKinematics(
  //     point = DataPoint(
  //       time = currentPoint.time,
  //       altitude = currentPoint.altitude,
  //       speed = currentSpeed,
  //     ),
  //     acceleration = currentAcceleration,
  //   )

}
