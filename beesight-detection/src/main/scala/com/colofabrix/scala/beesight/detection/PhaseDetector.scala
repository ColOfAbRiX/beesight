package com.colofabrix.scala.beesight.detection

import cats.data.*
import cats.syntax.all.*
import com.colofabrix.scala.beesight.collections.NEW.*
import com.colofabrix.scala.beesight.config.*
import com.colofabrix.scala.beesight.detection.PhaseDetector.*
import com.colofabrix.scala.beesight.detection.model.{ DetectingState, DetectorState, FillingState, FocusPosition, Step, Transition, WindowEntry }
import com.colofabrix.scala.beesight.detection.model.DetectionConfigActions.*
import com.colofabrix.scala.beesight.detection.stages.StageHandler
import com.colofabrix.scala.beesight.model.*
import scala.annotation.tailrec

/**
 * Orchestrates per-phase stage handlers, sweeping the focus across the window to find transitions.
 */
final private[detection] class PhaseDetector private (config: DetectionConfig) {

  private val takeoffHandler  = StageHandler(config, EventType.Takeoff)
  private val freefallHandler = StageHandler(config, EventType.Freefall)
  private val canopyHandler   = StageHandler(config, EventType.Canopy)
  private val landingHandler  = StageHandler(config, EventType.Landing)

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
    val phase          = FlightPhase.fromEvent(eventType)

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
                val eventPoint     = FlightEventPoint(detectedPoint.idx, detectedPoint.altitude)
                val detectedEvents = data.detectedEvents.setEvent(state.eventType, eventPoint)

                WindowEntry(
                  point = data.point,
                  kinematics = data.kinematics,
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

}

object PhaseDetector {

  final private case class LoopState(
    eventType: EventType,
    leftElements: Int,
    rightElements: Int,
    stageHandler: StageHandler,
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

  private[detection] def apply(config: DetectionConfig): PhaseDetector =
    new PhaseDetector(config)

}
