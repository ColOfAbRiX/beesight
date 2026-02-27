package com.colofabrix.scala.beesight.detection.stages

import com.colofabrix.scala.beesight.collections.NonEmptyWindow.NonEmptyFilled
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.model.EventType
import scala.collection.mutable.{ Map => MMap }
import com.colofabrix.scala.beesight.config.DetectionConfig

trait StageHandler {

  protected def detectTrigger(window: NonEmptyFilled[DetectionData]): Boolean

  protected def validateTrigger(window: NonEmptyFilled[DetectionData]): Boolean

  protected def findInflection(window: NonEmptyFilled[DetectionData]): Option[Int]

  def stageDetection(window: NonEmptyFilled[DetectionData]): Option[(Int, IndexedDataPoint)] =
    for {
      isTrigger      = detectTrigger(window) && validateTrigger(window)
      detectionIdx  <- if isTrigger then findInflection(window) else None
      detectionPoint = window.focusAt(detectionIdx).focus.point
      result         = (detectionIdx, detectionPoint)
    } yield result

}

object StageHandler {

  private val lookup: MMap[(EventType, DetectionConfig), StageHandler] =
    MMap.empty

  def apply(config: DetectionConfig, eventType: EventType): StageHandler =
    eventType match {
      case EventType.Takeoff =>
        lookup.getOrElseUpdate((eventType, config), TakeoffHandler(config.takeoff))
      case EventType.Freefall =>
        lookup.getOrElseUpdate((eventType, config), FreefallHandler(config.freefall))
      case EventType.Canopy =>
        lookup.getOrElseUpdate((eventType, config), CanopyHandler(config.canopy, config.freefall))
      case EventType.Landing =>
        lookup.getOrElseUpdate((eventType, config), LandingHandler(config.landing))
    }

}
