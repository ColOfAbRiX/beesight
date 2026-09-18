package com.colofabrix.scala.beesight.jumpevents

import cats.data.*
import com.colofabrix.scala.beesight.collections.NEW.*
import com.colofabrix.scala.beesight.config.*
import com.colofabrix.scala.beesight.jumpevents.EventPointDetector.*
import com.colofabrix.scala.beesight.jumpevents.handlers.EventHandler
import com.colofabrix.scala.beesight.jumpevents.model.*
import com.colofabrix.scala.beesight.jumpevents.model.DetectorConfigActions.*
import com.colofabrix.scala.beesight.model.*
import scala.annotation.tailrec

/**
 * Orchestrates per-event handlers, sweeping the focus across the window to find transitions.
 */
final private[jumpevents] class EventPointDetector private (config: DetectionConfig) {

  private val takeoffHandler  = EventHandler(config, EventType.Takeoff)
  private val freefallHandler = EventHandler(config, EventType.Freefall)
  private val canopyHandler   = EventHandler(config, EventType.Canopy)
  private val landingHandler  = EventHandler(config, EventType.Landing)

  def detect(): Transition[DetectingState, Vector[FlightEvents]] =
    IndexedState { state =>
      detectors(state)
        .foldLeft(None: Detector) { (accumulator, eventType) =>
          accumulator.orElse(runDetectionOnWindow(state, eventType))
        }
        .getOrElse {
          val nextState   = state.copy(focusType = FocusPosition.StageFocus)
          val emitNothing = Vector.empty[FlightEvents]
          (nextState, emitNothing)
        }
    }

  private val detectors: DetectingState => List[EventType] = {
    case DetectingState(_, FlightPhase.BeforeTakeoff, _) =>
      EventType.Takeoff :: EventType.Freefall :: Nil
    case DetectingState(_, FlightPhase.Climbing, _) =>
      EventType.Freefall :: EventType.Landing :: Nil
    case DetectingState(_, FlightPhase.Freefall, _) =>
      EventType.Canopy :: EventType.Landing :: Nil
    case DetectingState(_, FlightPhase.UnderCanopy, _) =>
      EventType.Landing :: Nil
    case _ @DetectingState(_, FlightPhase.Landed, _) =>
      Nil
  }

  private def runDetectionOnWindow(
    currentState: DetectingState,
    eventType: EventType
  ): Option[(FillingState, Vector[FlightEvents])] =
    val stageConfig    = config.getEventConfigs(eventType)
    val eventTypeFocus = config.getBackwindowSize(eventType)
    val phase          = nextFlightPhase(eventType)

    val stageHandler =
      eventType match {
        case EventType.Takeoff  => takeoffHandler
        case EventType.Freefall => freefallHandler
        case EventType.Canopy   => canopyHandler
        case EventType.Landing  => landingHandler
      }

    val initialFocus =
      currentState.focusType match {
        case FocusPosition.Zero       => 0
        case FocusPosition.StageFocus => config.getBackwindowSize(eventType)
      }

    val initialState =
      LoopState(
        eventType = eventType,
        leftElements = initialFocus,
        rightElements = stageConfig.validationWindowSize.value,
        stageHandler = stageHandler,
        maxFocus = eventTypeFocus,
        loopFocus = initialFocus,
        processingWindow = currentState.window,
        phase = phase,
      )

    loop(initialState).map { loopResult =>
      val nextState = FillingState(loopResult.remainingData, loopResult.nextPhase, FocusPosition.Zero)
      (nextState, loopResult.emitEvents)
    }

  @tailrec
  private def loop(state: LoopState): Option[LoopResult] =
    val focusedWindow =
      state
        .processingWindow
        .subwindowAt(state.leftElements, state.loopFocus, state.rightElements)

    val detectionResult =
      state
        .stageHandler
        .stageDetection(focusedWindow)
        .map {
          case (focusedWindowDetectedIdx, detectedPoint) =>
            val (beforeDetection, afterDetection) = focusedWindow.dequeue(focusedWindowDetectedIdx)

            val emitEvents = beforeDetection.map(_.detectedEvents)

            val nextToProcess =
              afterDetection.map { data =>
                val eventPoint     = EventPoint(detectedPoint.idx, detectedPoint.altitude)
                val detectedEvents = data.detectedEvents.setEventPoint(state.eventType, eventPoint)

                WindowEntry(
                  point = data.point,
                  smoothedSpeed = data.smoothedSpeed,
                  acceleration = data.acceleration,
                  phase = state.phase,
                  detectedEvents = detectedEvents,
                )
              }

            LoopResult(emitEvents, nextToProcess, state.phase)
        }

    detectionResult match {
      case Some(nextDetection) =>
        Some(nextDetection)
      case None if state.loopFocus == state.maxFocus =>
        None
      case None =>
        val nextState = state.copy(loopFocus = state.loopFocus + 1)
        loop(nextState)
    }

  private def nextFlightPhase(eventType: EventType): FlightPhase =
    eventType match {
      case EventType.Takeoff  => FlightPhase.Climbing
      case EventType.Freefall => FlightPhase.Freefall
      case EventType.Canopy   => FlightPhase.UnderCanopy
      case EventType.Landing  => FlightPhase.Landed
    }

}

object EventPointDetector {

  final private case class LoopState(
    eventType: EventType,
    leftElements: Int,
    rightElements: Int,
    stageHandler: EventHandler,
    maxFocus: Int,
    loopFocus: Int,
    processingWindow: NonEmptyFilled[WindowEntry],
    phase: FlightPhase,
  )

  final private case class LoopResult(
    emitEvents: Vector[FlightEvents],
    remainingData: NonEmptyFilling[WindowEntry],
    nextPhase: FlightPhase,
  )

  private type Detector =
    Option[(DetectorState, Vector[FlightEvents])]

  private[jumpevents] def apply(config: DetectionConfig): EventPointDetector =
    new EventPointDetector(config)

}
