package com.colofabrix.scala.beesight.detection.preprocess

import com.colofabrix.scala.beesight.collections.SlidingWindow
import com.colofabrix.scala.beesight.detection.model.*
import java.time.Instant

final private[preprocess] case class PreprocessState(
  window: SlidingWindow[SpikeWindowData],
)

final private[preprocess] case class SpikeWindowData(
  point: DataPoint,
  kinematics: Option[PointKinematics],
)
