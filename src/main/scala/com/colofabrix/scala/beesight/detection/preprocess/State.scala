package com.colofabrix.scala.beesight.detection.preprocess

import com.colofabrix.scala.beesight.collections.NonEmptyWindow
import com.colofabrix.scala.beesight.detection.model.*

final private[preprocess] case class PreprocessState(
  window: Option[NonEmptyWindow[SpikeWindowData]],
)

final private[preprocess] case class SpikeWindowData(
  point: IndexedDataPoint,
  kinematics: Option[PointKinematics],
)
