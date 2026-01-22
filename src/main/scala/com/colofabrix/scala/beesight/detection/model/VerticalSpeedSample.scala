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
 * Sample of vertical speed at a point in time, used for backtracking to find true transition points.
 */
final private[detection] case class VerticalSpeedSample(
  index: Long,
  verticalSpeed: Double,
  altitude: Double,
)
