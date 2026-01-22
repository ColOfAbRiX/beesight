package com.colofabrix.scala.beesight.detection.model

import breeze.linalg.DenseVector
import breeze.stats.median
import com.colofabrix.scala.beesight.collections.FixedSizeQueue
import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.model.FlightEvents
import com.colofabrix.scala.beesight.model.FlightPhase
import com.colofabrix.scala.beesight.model.InputFlightRow
import java.time.Instant

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

  def enqueue(prev: Windows, kinematics: Kinematics, index: Long): Windows =
    val verticalSpeedSample = VerticalSpeedSample(index, kinematics.smoothedVerticalSpeed, kinematics.altitude)
    Windows(
      smoothingVerticalSpeed = prev.smoothingVerticalSpeed.enqueue(kinematics.verticalSpeed),
      landingStability = prev.landingStability.enqueue(kinematics.smoothedVerticalSpeed),
      backtrackVerticalSpeed = prev.backtrackVerticalSpeed.enqueue(verticalSpeedSample),
    )

}
