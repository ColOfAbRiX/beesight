package com.colofabrix.scala.beesight.detection

import breeze.linalg.DenseVector
import breeze.stats.median
import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.model.FlightPhase
import com.colofabrix.scala.beesight.model.FlightEvents
import com.colofabrix.scala.beesight.model.InputFlightRow
import java.time.Instant
import com.colofabrix.scala.beesight.collections.FixedSizeQueue

/**
 * Complete state of the streaming detection at each point.
 * This is the single source of truth for all detection context.
 */
final private[detection] case class StreamState[A](
  inputPoint: InputFlightRow[A],
  dataPointIndex: Long,
  kinematics: Kinematics,
  windows: Windows,
  detectedPhase: FlightPhase,
  detectedStages: FlightEvents,
  takeoffMissing: Boolean,
)

private[detection] object StreamState {

  def create[A](point: InputFlightRow[A], config: DetectionConfig): StreamState[A] =
    StreamState(
      inputPoint = point,
      dataPointIndex = 0,
      kinematics = Kinematics.create(point),
      windows = Windows.create(config),
      detectedPhase = FlightPhase.BeforeTakeoff,
      detectedStages = FlightEvents.empty,
      takeoffMissing = point.altitude > config.TakeoffMaxAltitude && point.verticalSpeed < 0,
    )

}

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

}

/**
 * Sample of vertical speed at a point in time, used for backtracking to find true transition points.
 */
final private[detection] case class VerticalSpeedSample(
  index: Long,
  verticalSpeed: Double,
  altitude: Double,
)

/**
 * Sliding windows used for various calculations in the detection pipeline.
 */
final private[detection] case class Windows(
  smoothingVerticalSpeed: FixedSizeQueue[Double],
  landingStability: FixedSizeQueue[Double],
  backtrackVerticalSpeed: FixedSizeQueue[VerticalSpeedSample],
)

private[detection] object Windows {

  def create(config: DetectionConfig): Windows =
    Windows(
      smoothingVerticalSpeed = FixedSizeQueue(config.SmoothingVerticalSpeedWindowSize),
      landingStability = FixedSizeQueue(config.LandingStabilityWindowSize),
      backtrackVerticalSpeed = FixedSizeQueue(config.BacktrackVerticalSpeedWindowSize),
    )

  def update(prev: Windows, kinematics: Kinematics, index: Long): Windows =
    val verticalSpeedSample = VerticalSpeedSample(index, kinematics.smoothedVerticalSpeed, kinematics.altitude)
    Windows(
      smoothingVerticalSpeed = prev.smoothingVerticalSpeed.enqueue(kinematics.verticalSpeed),
      landingStability = prev.landingStability.enqueue(kinematics.smoothedVerticalSpeed),
      backtrackVerticalSpeed = prev.backtrackVerticalSpeed.enqueue(verticalSpeedSample),
    )

}

private def vectorLength(xs: Double*): Double =
  Math.sqrt(xs.map(x => x * x).sum)
