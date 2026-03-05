package com.colofabrix.scala.beesight.detection.stages

import com.colofabrix.scala.beesight.collections.NonEmptyWindow.NonEmptyFilled
import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.model.EventType
import scala.collection.mutable.Map as MMap

trait StageHandler {

  protected def detectTrigger(window: NonEmptyFilled[DetectionData]): Boolean

  protected def validateTrigger(window: NonEmptyFilled[DetectionData]): Boolean

  protected def findInflection(window: NonEmptyFilled[DetectionData]): Option[Int]

  def stageDetection(window: NonEmptyFilled[DetectionData]): Option[(Int, IndexedDataPoint)] =
    for {
      detectionIdx  <- if (detectTrigger(window) && validateTrigger(window)) then findInflection(window) else None
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
