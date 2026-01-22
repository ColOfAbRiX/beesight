package com.colofabrix.scala.beesight.detection.model

import breeze.linalg.DenseVector
import breeze.stats.median
import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.model.FlightPhase
import com.colofabrix.scala.beesight.model.FlightEvents
import com.colofabrix.scala.beesight.model.InputFlightRow
import java.time.Instant
import com.colofabrix.scala.beesight.collections.FixedSizeQueue

/**
 * Kinematic data - both raw input values and derived computed values.
 * This is the core data structure for flight physics calculations.
 */
final private[detection] case class Kinematics(
  time: Instant,
  altitude: Double,
  verticalSpeed: Double,
  northSpeed: Double,
  eastSpeed: Double,
  smoothedVerticalSpeed: Double,
  smoothedVerticalAcceleration: Double,
  horizontalSpeed: Double,
  totalSpeed: Double,
)

private[detection] object Kinematics {

  /** Create initial kinematics from first point */
  def create(point: InputFlightRow[?]): Kinematics =
    val horizontalSpeed = vectorLength(point.northSpeed, point.eastSpeed)
    val totalSpeed      = vectorLength(point.northSpeed, point.eastSpeed, point.verticalSpeed)

    Kinematics(
      time = point.time,
      altitude = point.altitude,
      verticalSpeed = point.verticalSpeed,
      northSpeed = point.northSpeed,
      eastSpeed = point.eastSpeed,
      smoothedVerticalSpeed = point.verticalSpeed,
      smoothedVerticalAcceleration = 0.0,
      horizontalSpeed = horizontalSpeed,
      totalSpeed = totalSpeed,
    )

  /** Compute kinematics from input point and previous state */
  def compute(point: InputFlightRow[?], prevState: StreamState[?]): Kinematics =
    val horizontalSpeed = vectorLength(point.northSpeed, point.eastSpeed)
    val totalSpeed      = vectorLength(point.northSpeed, point.eastSpeed, point.verticalSpeed)

    val smoothingWindow = prevState.windows.smoothingVerticalSpeed

    val smoothedVerticalSpeed =
      if smoothingWindow.isEmpty then
        point.verticalSpeed
      else
        median(DenseVector(smoothingWindow.toArray :+ point.verticalSpeed))

    val smoothedVerticalAcceleration = smoothedVerticalSpeed - prevState.kinematics.smoothedVerticalSpeed

    Kinematics(
      time = point.time,
      altitude = point.altitude,
      verticalSpeed = point.verticalSpeed,
      northSpeed = point.northSpeed,
      eastSpeed = point.eastSpeed,
      smoothedVerticalSpeed = smoothedVerticalSpeed,
      smoothedVerticalAcceleration = smoothedVerticalAcceleration,
      horizontalSpeed = horizontalSpeed,
      totalSpeed = totalSpeed,
    )

  private def vectorLength(xs: Double*): Double =
    Math.sqrt(xs.map(x => x * x).sum)

}
