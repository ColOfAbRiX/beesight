package com.colofabrix.scala.beesight.detection.model

import com.colofabrix.scala.beesight.collections.FixedSizeQueue

final case class VerticalSpeedSample(
  index: Long,
  speed: Double,
  altitude: Double,
)

final case class EventState(
  smoothingWindow: FixedSizeQueue[Double],
  backtrackWindow: FixedSizeQueue[VerticalSpeedSample],
  stabilityWindow: FixedSizeQueue[Double],
)

object EventState {

  def withSizes(smoothingSize: Int, backtrackSize: Int, stabilitySize: Int): EventState =
    EventState(
      smoothingWindow = FixedSizeQueue[Double](smoothingSize),
      backtrackWindow = FixedSizeQueue[VerticalSpeedSample](backtrackSize),
      stabilityWindow = FixedSizeQueue[Double](stabilitySize),
    )

}
