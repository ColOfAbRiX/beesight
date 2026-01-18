package com.colofabrix.scala.beesight.detection

import com.colofabrix.scala.beesight.model.FlightPhase
import com.colofabrix.scala.beesight.model.InputFlightPoint
import com.colofabrix.scala.beesight.stats.CusumDetector.CusumState
import java.time.Instant
import scala.collection.immutable.Queue

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
  vertSpeedWindow: FixedSizeQueue[Double] = FixedSizeQueue.empty,
  vertSpeedHistory: Vector[VerticalSpeedSample] = Vector.empty,
  freefallCusum: CusumState = CusumState.Empty,
  canopyCusum: CusumState = CusumState.Empty,
  detectedPhase: FlightPhase = FlightPhase.BeforeTakeoff,
  wasInFreefall: Boolean = false,
  assumedTakeoffMissed: Boolean = false,
)
