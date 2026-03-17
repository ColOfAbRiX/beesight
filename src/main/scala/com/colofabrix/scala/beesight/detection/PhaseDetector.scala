package com.colofabrix.scala.beesight.detection

import cats.data.*
import cats.syntax.all.*
import com.colofabrix.scala.beesight.collections.NEW.*
import com.colofabrix.scala.beesight.config.*
import com.colofabrix.scala.beesight.detection.PhaseDetector.*
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.detection.model.DetectionConfigActions.*
import com.colofabrix.scala.beesight.detection.stages.StageHandler
import com.colofabrix.scala.beesight.model.*
import scala.annotation.tailrec

/**
 * Orchestrates per-phase stage handlers, sweeping the focus across the window to find transitions.
 */
final private[detection] class PhaseDetector private (config: DetectionConfig) {

  def detect(): Transition[DetectionState, Vector[FlightEvents]] =
    IndexedState { state =>
      detectors(state)
        .foldLeft(None: Detector)(_ <+> _)
        .getOrElse {
          val nextState   = state.copy(focusType = FocusPosition.StageFocus)
          val emitNothing = Vector.empty[FlightEvents]
          (nextState, emitNothing)
        }
    }

  private val detectors: DetectionState => List[Detector] = {
    case state @ DetectionState(_, FlightPhase.BeforeTakeoff, _) =>
      runDetectionOnWindow(state, EventType.Takeoff) ::
      runDetectionOnWindow(state, EventType.Freefall) ::
      Nil
    case state @ DetectionState(_, FlightPhase.Climbing, _) =>
      runDetectionOnWindow(state, EventType.Freefall) ::
      runDetectionOnWindow(state, EventType.Landing) ::
      Nil
    case state @ DetectionState(_, FlightPhase.Freefall, _) =>
      runDetectionOnWindow(state, EventType.Canopy) ::
      runDetectionOnWindow(state, EventType.Landing) ::
      Nil
    case state @ DetectionState(_, FlightPhase.UnderCanopy, _) =>
      runDetectionOnWindow(state, EventType.Landing) ::
      Nil
    case _ @DetectionState(_, FlightPhase.Landed, _) =>
      Nil
  }

  private def runDetectionOnWindow(
    currentState: DetectionState,
    eventType: EventType,
  ): Option[(FillingState, Vector[FlightEvents])] =
    val stageConfig    = config.getEventConfigs(eventType)
    val eventTypeFocus = config.getBackwindowSize(eventType)
    val phase          = FlightPhase.fromEvent(eventType)
    val stageHandler   = StageHandler(config, eventType)

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

                DetectionData(
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
    processingWindow: NonEmptyFilled[DetectionData],
    phase: FlightPhase,
  )

  final private case class LoopResult(
    emitEvents: Vector[FlightEvents],
    remainingData: NonEmptyFilling[DetectionData],
    nextPhase: FlightPhase,
  )

  private type Detector =
    Option[(StateTrait, Vector[FlightEvents])]

  private[detection] def apply(config: DetectionConfig): PhaseDetector =
    new PhaseDetector(config)

}
