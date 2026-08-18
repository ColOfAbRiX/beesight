package com.colofabrix.scala.beesight.detection.preprocess

import com.colofabrix.scala.beesight.detection.model.*

/**
 * Pairs a data point with its computed kinematics for use in the despiking window.
 */
final private[preprocess] case class SpikeEntry(
  point: IndexedDataPoint,
  kinematics: Option[PointKinematics],
)
