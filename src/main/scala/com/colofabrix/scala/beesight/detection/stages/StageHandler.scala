package com.colofabrix.scala.beesight.detection.stages

import com.colofabrix.scala.beesight.collections.NonEmptyWindow.NonEmptyFilled
import com.colofabrix.scala.beesight.detection.model.DetectionData

trait StageHandler {

  protected def detectTrigger(window: NonEmptyFilled[DetectionData]): Boolean

  protected def validateTrigger(window: NonEmptyFilled[DetectionData]): Boolean

  protected def findInflection(window: NonEmptyFilled[DetectionData]): Int

  def stageDetection(window: NonEmptyFilled[DetectionData]): Option[Int] =
    Option.when(detectTrigger(window) && validateTrigger(window)) {
      findInflection(window)
    }

}
