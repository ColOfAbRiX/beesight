package com.colofabrix.scala.beesight.detection

import com.colofabrix.scala.beesight.model.FlightPhase
import com.colofabrix.scala.beesight.model.InputFlightPoint
import com.colofabrix.scala.beesight.stats.CusumDetector.CusumState
import java.time.Instant

final private[detection] case class FlightMetrics(
  altitude: Double,
  vertSpeed: Double,
  horizontalSpeed: Double,
  totalSpeed: Double,
  filteredVertSpeed: Double,
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
  filteredVertSpeed: Double = 0.0,
  verticalAccel: Double = 0.0,
  horizontalSpeed: Double = 0.0,
  totalSpeed: Double = 0.0,
  smoothingVertSpeedWindow: FixedSizeQueue[Double] = FixedSizeQueue.empty,
  phaseDetectionVertSpeedWindow: FixedSizeQueue[Double] = FixedSizeQueue.empty,
  backtrackVertSpeedWindow: FixedSizeQueue[VerticalSpeedSample] = FixedSizeQueue.empty,
  freefallCusum: CusumState = CusumState.Empty,
  canopyCusum: CusumState = CusumState.Empty,
  detectedPhase: FlightPhase = FlightPhase.BeforeTakeoff,
  wasInFreefall: Boolean = false,
  assumedTakeoffMissed: Boolean = false,
)

private[detection] object StreamState {

  def createDefault[A](
    point: InputFlightPoint[A],
    smoothingWindowSize: Int,
    phaseDetectionWindowSize: Int,
    backtrackWindowSize: Int,
  ): StreamState[A] =
    StreamState(
      inputPoint = point,
      smoothingVertSpeedWindow = FixedSizeQueue(smoothingWindowSize),
      phaseDetectionVertSpeedWindow = FixedSizeQueue(phaseDetectionWindowSize),
      backtrackVertSpeedWindow = FixedSizeQueue(backtrackWindowSize),
    )

}
