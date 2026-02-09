package com.colofabrix.scala.beesight.detection.preprocess

import com.colofabrix.scala.beesight.collections.SWindow
import com.colofabrix.scala.beesight.detection.model.*
import java.time.Instant

final private[preprocess] case class PreprocessState(
  window: SWindow[SpikeWindowData],
)

final private[preprocess] case class SpikeWindowData(
  point: DataPoint,
  kinematics: Option[PointKinematics],
)
