package com.colofabrix.scala.beesight.detection

import cats.data.*
import cats.syntax.all.*
import com.colofabrix.scala.beesight.collections.NEW
import com.colofabrix.scala.beesight.collections.NEW.*
import com.colofabrix.scala.beesight.config.*
import com.colofabrix.scala.beesight.detection.PhaseDetector.*
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.detection.model.DetectionConfigActions.*
import com.colofabrix.scala.beesight.detection.stages.StageHandler
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.utils.*
import monocle.syntax.all.*
import scala.annotation.tailrec

final class PhaseDetector private (config: DetectionConfig) {

  private val DEBUG_ENABLED =
    Option(System.getenv("BEESIGHT_DEBUG")).exists(_.equalsIgnoreCase("true"))

  private def debug(msg: => String): Unit =
    if DEBUG_ENABLED then println(msg)

  private def debugSection(title: String)(body: => Unit): Unit =
    if DEBUG_ENABLED then
      println(s"\n${"═" * 60}")
      println(s"║ $title")
      println(s"${"═" * 60}")
      body

  private def windowSummary(window: NEW[DetectionData]): String =
    val indices = window.toVector.map(_.point.idx)
    s"[${indices.head}..${indices.last}] (${window.length} pts)"

  private def eventsSummary(events: FlightEvents): String =
    val parts = Vector(
      events.takeoff.map(e => s"T@${e.index}"),
      events.freefall.map(e => s"F@${e.index}"),
      events.canopy.map(e => s"C@${e.index}"),
      events.landing.map(e => s"L@${e.index}"),
    ).flatten
    if parts.isEmpty then "∅" else parts.mkString(",")

  def detect(): Transition[DetectionState, Vector[FlightEvents]] =
    for {
      detectors <- possibleDetections
      result    <- runDetectors(detectors)
    } yield result

  private val possibleDetections: KeepState[DetectionState, List[DetectorResult]] =
    IndexedState
      .inspect[DetectionState, List[DetectorResult]] {
        case state @ DetectionState(_, phase @ FlightPhase.BeforeTakeoff, _) =>
          // TODO: Additional checks for Takeoff
          runDetectionOnWindow(state, EventType.Freefall) ::
          Nil
        case state @ DetectionState(_, FlightPhase.Climbing, _) =>
          // TODO: Additional checks for Canopy and Landing
          runDetectionOnWindow(state, EventType.Freefall) ::
          Nil
        case state @ DetectionState(_, FlightPhase.Freefall, _) =>
          // TODO: Additional checks for Landing
          runDetectionOnWindow(state, EventType.Canopy) ::
          Nil
        case state @ DetectionState(_, FlightPhase.UnderCanopy, _) =>
          runDetectionOnWindow(state, EventType.Landing) ::
          Nil
        case state @ DetectionState(_, FlightPhase.Landed, _) =>
          // TODO: In this case here just return every point using the last detected value
          Nil
      }

  private def runDetectors(detectors: List[DetectorResult]): Transition[DetectionState, Vector[FlightEvents]] =
    IndexedState { state =>
      detectors
        .reduceLeft(_ <+> _)
        .getOrElse {
          state.copy(focusIdx = state.window.focusIndex) -> Vector.empty[FlightEvents]
        }
    }

  private def runDetectionOnWindow(
    currentState: DetectionState,
    eventType: EventType,
  ): Option[(FillingState, Vector[FlightEvents])] =
    val stageConfig    = config.getEventConfigs(eventType)
    val backwindowSize = config.getBackwindowSize(eventType)

    debug(s"  ┌─ runDetection: Looking for $eventType")
    debug(s"  │  backwindow=$backwindowSize, validation=${stageConfig.validationWindowSize}")
    debug(s"  │  startFocus=${currentState.focusIdx}, maxFocus=$backwindowSize")

    val initialState =
      LoopState(
        eventType = eventType,
        leftElements = backwindowSize,
        rightElements = stageConfig.validationWindowSize,
        maxFocus = backwindowSize,
        loopFocus = currentState.focusIdx,
        processingWindow = currentState.window,
        phase = FlightPhase.fromEvent(eventType),
      )

    loop(initialState).map { loopResult =>
      debug(s"  │  ★★★ DETECTION FOUND! ★★★")
      debug(s"  │  Emitting ${loopResult.emitEvents.size} events")
      debug(s"  │  Remaining: ${windowSummary(loopResult.remainingData)}")
      debug(s"  │  Next phase: ${loopResult.nextPhase}, nextFocus: ${loopResult.nextFocus}")
      debug(s"  └─ → FillingState, emitting ${loopResult.emitEvents.size} events")
      val nextState = FillingState(loopResult.remainingData, loopResult.nextPhase, loopResult.nextFocus)
      Some((nextState, loopResult.emitEvents))
    }.getOrElse {
      debug(s"  └─ → No detection, state unchanged")
      None
    }

  @tailrec
  private def loop(state: LoopState): Option[LoopResult] =
    if state.loopFocus > state.maxFocus then
      debug(s"  │  Loop: focus ${state.loopFocus} > expected ${state.maxFocus}, no detection")
      return None

    val stageHandler = StageHandler(config, state.eventType)
    val stageConfig  = config.getEventConfigs(state.eventType)

    val focusedWindow =
      state
        .processingWindow
        .subwindowAt(state.leftElements, state.loopFocus, state.rightElements)

    debug(s"  │  Loop@${state.loopFocus}: subwindow ${windowSummary(focusedWindow)}")

    val detectionResult =
      stageHandler
        .stageDetection(focusedWindow)
        .map {
          case (focusedWindowDetectedIdx, detectedPoint) =>
            val (beforeDetection, afterDetection) = focusedWindow.dequeue(focusedWindowDetectedIdx)

            debug(s"  │    ✓ Found at subwindow idx $focusedWindowDetectedIdx (P${detectedPoint.idx}, alt=${detectedPoint.altitude})")
            debug(s"  │  Split: before=${beforeDetection.size} pts, after=${afterDetection.length} pts")

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

            LoopResult(emitEvents, nextToProcess, state.phase, 0)
        }

    detectionResult match {
      case Some(nextDetection) =>
        Some(nextDetection)
      case None =>
        debug(s"  │    × No detection at focus ${state.loopFocus}, trying ${state.loopFocus + 1}")
        loop(state.focus(_.loopFocus).modify(_ + 1))
    }

}

object PhaseDetector {

  private final case class LoopState(
    eventType: EventType,
    leftElements: Int,
    rightElements: Int,
    // TODO: Static data shouldn't be in this case class
    maxFocus: Int,
    loopFocus: Int,
    processingWindow: NonEmptyFilled[DetectionData],
    phase: FlightPhase,
  )

  private final case class LoopResult(
    emitEvents: Vector[FlightEvents],
    remainingData: NonEmptyFilling[DetectionData],
    nextPhase: FlightPhase,
    nextFocus: Int,
  )

  private type DetectorResult =
    Option[(StateTrait, Vector[FlightEvents])]

  def apply(config: DetectionConfig): PhaseDetector =
    new PhaseDetector(config)

}
