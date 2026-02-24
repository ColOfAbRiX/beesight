package com.colofabrix.scala.beesight.detection

import cats.data.*
import cats.syntax.all.*
import com.colofabrix.scala.beesight.collections.NEW
import com.colofabrix.scala.beesight.collections.NEW.*
import com.colofabrix.scala.beesight.config.*
import com.colofabrix.scala.beesight.detection.DebugLog
import com.colofabrix.scala.beesight.detection.DebugLog.*
import com.colofabrix.scala.beesight.detection.PhaseDetector.*
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.detection.model.DetectionConfigActions.*
import com.colofabrix.scala.beesight.detection.stages.StageHandler
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.utils.*
import scala.annotation.tailrec

final class PhaseDetector private (config: DetectionConfig) {

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
    case state @ DetectionState(_, FlightPhase.Landed, _) =>
      Nil
  }

  private def runDetectionOnWindow(
    currentState: DetectionState,
    eventType: EventType,
  ): Option[(FillingState, Vector[FlightEvents])] =
    val stageConfig    = config.getEventConfigs(eventType)
    val eventTypeFocus = config.getBackwindowSize(eventType)

    val initialFocus =
      currentState.focusType match {
        case FocusPosition.Zero       => 0
        case FocusPosition.StageFocus => config.getBackwindowSize(eventType)
      }

    DebugLog(s"  ┌─ runDetection: Looking for $eventType")
    DebugLog(s"  │  backwindow=$initialFocus, validation=${stageConfig.validationWindowSize}")
    DebugLog(s"  │  startFocus=${currentState.focusType}, maxFocus=$initialFocus")

    val initialState =
      LoopState(
        eventType = eventType,
        leftElements = initialFocus,
        rightElements = stageConfig.validationWindowSize,
        maxFocus = eventTypeFocus,
        loopFocus = initialFocus,
        processingWindow = currentState.window,
        phase = FlightPhase.fromEvent(eventType),
      )

    loop(initialState).map { loopResult =>
      DebugLog(s"  │  ★★★ DETECTION FOUND! ★★★")
      DebugLog(s"  │  Emitting ${loopResult.emitEvents.size} events")
      DebugLog(s"  │  Remaining: ${DebugLog.windowSummary(loopResult.remainingData)}")
      DebugLog(s"  │  Next phase: ${loopResult.nextPhase}, nextFocus: 0")
      DebugLog(s"  └─ → FillingState, emitting ${loopResult.emitEvents.size} events")
      val nextState = FillingState(loopResult.remainingData, loopResult.nextPhase, FocusPosition.Zero)
      Some((nextState, loopResult.emitEvents))
    }.getOrElse {
      DebugLog(s"  └─ → No detection, state unchanged")
      None
    }

  @tailrec
  private def loop(state: LoopState): Option[LoopResult] =
    val stageHandler = StageHandler(config, state.eventType)
    val stageConfig  = config.getEventConfigs(state.eventType)

    val focusedWindow =
      state
        .processingWindow
        .subwindowAt(state.leftElements, state.loopFocus, state.rightElements)

    DebugLog(s"  │  Loop@${state.loopFocus}: subwindow ${DebugLog.windowSummary(focusedWindow)}")

    val detectionResult =
      stageHandler
        .stageDetection(focusedWindow)
        .map {
          case (focusedWindowDetectedIdx, detectedPoint) =>
            val (beforeDetection, afterDetection) = focusedWindow.dequeue(focusedWindowDetectedIdx)

            DebugLog(s"  │    ✓ Found at subwindow idx $focusedWindowDetectedIdx (P${detectedPoint.idx}, alt=${detectedPoint.altitude.fmt})")
            DebugLog(s"  │  Split: before=${beforeDetection.size} pts, after=${afterDetection.length} pts")

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
        DebugLog(s"  │  Loop: focus ${state.loopFocus} > expected ${state.maxFocus}, no detection")
        None
      case None =>
        DebugLog(s"  │    × No detection at focus ${state.loopFocus}, trying ${state.loopFocus + 1}")
        val nextState = state.copy(loopFocus = state.loopFocus + 1)
        loop(nextState)
    }

}

object PhaseDetector {

  final private case class LoopState(
    eventType: EventType,
    leftElements: Int,
    rightElements: Int,
    // TODO: Static data shouldn't be in this case class
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

  def apply(config: DetectionConfig): PhaseDetector =
    new PhaseDetector(config)

}
