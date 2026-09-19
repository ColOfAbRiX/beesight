package com.colofabrix.scala.beesight.jumpevents.handlers

import com.colofabrix.scala.beesight.collections.NonEmptyWindow.NonEmptyFilled
import com.colofabrix.scala.beesight.config.JumpEventsConfig
import com.colofabrix.scala.beesight.jumpevents.model.WindowEntry
import com.colofabrix.scala.beesight.model.*

/**
 * Template for a flight-event detector implementing the trigger → validate → inflection pipeline.
 */
private[jumpevents] trait EventHandler {

  protected def detectTrigger(window: NonEmptyFilled[WindowEntry]): Boolean

  protected def validateTrigger(window: NonEmptyFilled[WindowEntry]): Boolean

  protected def findInflection(window: NonEmptyFilled[WindowEntry]): Option[Int]

  def stageDetection(window: NonEmptyFilled[WindowEntry]): Option[(Int, IndexedPoint)] =
    for {
      detectionIdx  <- if (detectTrigger(window) && validateTrigger(window)) then findInflection(window) else None
      detectionPoint = window.focusAt(detectionIdx).focus.point
      result         = (detectionIdx, detectionPoint)
    } yield result

}

private[jumpevents] object EventHandler {

  def apply(config: JumpEventsConfig, eventType: EventType): EventHandler =
    eventType match {
      case EventType.Takeoff  => TakeoffHandler(config.takeoff)
      case EventType.Freefall => FreefallHandler(config.freefall)
      case EventType.Canopy   => CanopyHandler(config.canopy, config.freefall)
      case EventType.Landing  => LandingHandler(config.landing)
    }

}
