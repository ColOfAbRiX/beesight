package com.colofabrix.scala.beesight.detection.stages

import com.colofabrix.scala.beesight.collections.NonEmptyWindow.NonEmptyFilled
import com.colofabrix.scala.beesight.config.CanopyConfig
import com.colofabrix.scala.beesight.detection.math.Stats
import com.colofabrix.scala.beesight.detection.model.{ DetectionData, IndexedDataPoint }
import com.colofabrix.scala.beesight.model.EventType
import java.time.Duration
import scala.annotation.nowarn

final class CanopyHandler private (@nowarn config: CanopyConfig) extends StageHandler {

  protected def detectTrigger(window: NonEmptyFilled[DetectionData]): Boolean =
    false

  protected def validateTrigger(window: NonEmptyFilled[DetectionData]): Boolean =
    false

  protected def findInflection(window: NonEmptyFilled[DetectionData]): Option[Int] =
    Some(window.focusIndex)

}

object CanopyHandler {

  def apply(config: CanopyConfig): CanopyHandler =
    new CanopyHandler(config)

}
