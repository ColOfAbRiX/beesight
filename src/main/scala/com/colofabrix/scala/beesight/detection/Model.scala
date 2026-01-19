package com.colofabrix.scala.beesight.detection

import com.colofabrix.scala.beesight.model.FlightPhase
import com.colofabrix.scala.beesight.model.InputFlightPoint
import java.time.Instant

final private[detection] case class FlightMetricsSnapshot(
  altitude: Double,
  verticalSpeed: Double,
  horizontalSpeed: Double,
  totalSpeed: Double,
  smoothedVerticalSpeed: Double,
  verticalAcceleration: Double,
)

final private[detection] case class VerticalSpeedSample(
  index: Long,
  verticalSpeed: Double,
  altitude: Double,
)

final private[detection] case class StreamState[A](
  inputPoint: InputFlightPoint[A],
  dataPointIndex: Long = 0,
  time: Instant = Instant.EPOCH,
  height: Double = 0.0,
  verticalSpeed: Double = 0.0,
  filteredVerticalSpeed: Double = 0.0,
  verticalAccel: Double = 0.0,
  horizontalSpeed: Double = 0.0,
  totalSpeed: Double = 0.0,
  smoothingVerticalSpeedWindow: FixedSizeQueue[Double] = FixedSizeQueue.empty,
  landingStabilityWindow: FixedSizeQueue[Double] = FixedSizeQueue.empty,
  backtrackVerticalSpeedWindow: FixedSizeQueue[VerticalSpeedSample] = FixedSizeQueue.empty,
  detectedPhase: FlightPhase = FlightPhase.BeforeTakeoff,
  takeoffMissing: Boolean = false,
)

private[detection] object StreamState {

  def createDefault[A](
    point: InputFlightPoint[A],
    smoothingWindowSize: Int,
    landingStabilityWindowSize: Int,
    backtrackWindowSize: Int,
  ): StreamState[A] =
    StreamState(
      inputPoint = point,
      smoothingVerticalSpeedWindow = FixedSizeQueue(smoothingWindowSize),
      landingStabilityWindow = FixedSizeQueue(landingStabilityWindowSize),
      backtrackVerticalSpeedWindow = FixedSizeQueue(backtrackWindowSize),
    )

}
