package com.colofabrix.scala.beesight.detection

import cats.data.*
import cats.syntax.all.*
import com.colofabrix.scala.beesight.collections.*
import com.colofabrix.scala.beesight.collections.NonEmptyWindow.*
import com.colofabrix.scala.beesight.config.{ DetectionConfig, EventConfigs }
import com.colofabrix.scala.beesight.detection.FlightStagesDetection.*
import com.colofabrix.scala.beesight.detection.FlightStagesDetection.given
import com.colofabrix.scala.beesight.detection.math.*
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.detection.model.DetectionConfigActions.*
import com.colofabrix.scala.beesight.detection.preprocess.PreprocessData
import com.colofabrix.scala.beesight.detection.stages.*
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.model.formats.FileFormatAdapter
import com.colofabrix.scala.beesight.utils.*
import monocle.syntax.all.*
import scala.annotation.tailrec

/**
 * Detects flight stages (takeoff, freefall, canopy, landing) from flight data streams.
 */
final class FlightStagesDetection private (config: DetectionConfig) {

  //  Type Aliases  //

  private type Transition[S <: StateTrait, A] =
    IndexedState[S, StateTrait, A]

  private type KeepState[S <: StateTrait, A] =
    IndexedState[S, S, A]

  //  Configuration / Derived Values  //

  private val slidingWindowSize: Int =
    val smoothWindows     = config.eventConfigsAsList.map(_.smoothingWindowSize)
    val backWindows       = config.eventConfigsAsList.map(_.backtrackWindowSize)
    val validationWindows = config.eventConfigsAsList.map(_.validationWindowSize)
    Math.max(smoothWindows.max, backWindows.max) + 1 + validationWindows.max

  //  Dependencies  //

  private val preprocessor: PreprocessData =
    PreprocessData(config)

  //  Public API  //

  /**
   * Streaming pipe that detects flight stages using the provided configuration.
   */
  def streamDetect[F[_], A: FileFormatAdapter]: fs2.Pipe[F, A, OutputFlightPoint[A]] =
    rawStream => rawStream.processBypass(mainDetectionPipe)

  //  Stream Pipeline  //

  private def mainDetectionPipe[F[_]]: fs2.Pipe[F, DataPoint, FlightEvents] =
    pointStream =>
      pointStream
        .through(applyIndex)
        .through(preprocessor.preprocess)
        .statefulMapEmits(initialStreamState) {
          case (currentPoint, state: FillingState) =>
            handleFillingState(currentPoint)
              .as(Vector.empty[FlightEvents])
              .run(state)
              .value
          case (currentPoint, state: DetectionState) =>
            handleDetectionState(currentPoint)
              .run(state)
              .value
        }

  private def applyIndex[F[_]]: fs2.Pipe[F, DataPoint, IndexedDataPoint] =
    pointStream =>
      pointStream
        .zipWithIndex
        .map {
          case (DataPoint(time, altitude, speed), idx) => IndexedDataPoint(time, idx, altitude, speed)
        }

  private def initialStreamState(first: IndexedDataPoint): (StateTrait, Vector[FlightEvents]) =
    val firstKinematics =
      PointKinematics(
        time = first.time,
        altitude = first.altitude,
        speed = first.speed,
        acceleration = GeoVector.zero,
      )

    val firstData =
      DetectionData(
        point = first,
        kinematics = firstKinematics,
        phase = FlightPhase.BeforeTakeoff,
        detectedEvents = FlightEvents.empty,
      )

    NEW(slidingWindowSize, firstData) match {
      case window: NonEmptyFilled[?] =>
        DetectionState(window, FlightPhase.BeforeTakeoff, 0) -> Vector.empty
      case window: NonEmptyFilling[?] =>
        FillingState(window, FlightPhase.BeforeTakeoff, 0) -> Vector.empty
    }

  //  State Handlers  //

  private def handleFillingState(currentPoint: IndexedDataPoint): Transition[FillingState, Unit] =
    IndexedState.modify { previousState =>
      val currentData = buildCurrentDetectionData(previousState.window, currentPoint)
      val window      = previousState.window.enqueue(currentData)

      window match {
        case currentWindow: NonEmptyFilling[?] =>
          FillingState(currentWindow, previousState.phase, previousState.focusIdx)
        case currentWindow: NonEmptyFilled[?] =>
          DetectionState(currentWindow, previousState.phase, previousState.focusIdx)
      }
    }

  private def handleDetectionState(currentPoint: IndexedDataPoint): Transition[DetectionState, Vector[FlightEvents]] =
    for {
      oldestData      <- pushCurrentDetectionState(currentPoint)
      additionalEmits <- detectPhase
      allToEmit        = oldestData.detectedEvents +: additionalEmits
    } yield allToEmit

  //  State Operations  //

  private def pushCurrentDetectionState(currentPoint: IndexedDataPoint): KeepState[DetectionState, DetectionData] =
    IndexedState { previousState =>
      val currentData                 = buildCurrentDetectionData(previousState.window, currentPoint)
      val (oldestData, currentWindow) = previousState.window.pushReturning(currentData)
      val currentState                = previousState.focus(_.window).replace(currentWindow)
      currentState -> oldestData
    }

  private val detectPhase: Transition[DetectionState, Vector[FlightEvents]] =
    IndexedState {
      case state @ DetectionState(_, FlightPhase.BeforeTakeoff, _) =>
        runDetectionOnWindow(state, EventType.Freefall)

      case state @ DetectionState(_, FlightPhase.Climbing, _) =>
        runDetectionOnWindow(state, EventType.Freefall)

      case state @ DetectionState(_, FlightPhase.Freefall, _) =>
        runDetectionOnWindow(state, EventType.Canopy)

      case state @ DetectionState(_, FlightPhase.UnderCanopy, _) =>
        runDetectionOnWindow(state, EventType.Landing)

      case state @ DetectionState(_, FlightPhase.Landed, _) =>
        // TODO: In this case here just return every point using the last detected value
        state -> Vector.empty
    }

  private def runDetectionOnWindow(
    currentState: DetectionState,
    eventType: EventType,
  ): (StateTrait, Vector[FlightEvents]) =
    val stageConfig = config.getEventConfigs(eventType)

    val backwindowSize = Math.max(stageConfig.backtrackWindowSize, stageConfig.smoothingWindowSize)

    val initialState =
      LoopState(
        eventType = eventType,
        expectedFocus = backwindowSize,
        leftElements = backwindowSize,
        loopFocus = currentState.focusIdx,
        rightElements = stageConfig.validationWindowSize,
        processingWindow = currentState.window,
        phase = FlightPhase.fromEvent(eventType),
      )

    loop(initialState).fold(currentState, Vector.empty) { loopResult =>
      val nextState = FillingState(loopResult.remainingData, loopResult.nextPhase, loopResult.nextFocus)
      nextState -> loopResult.emitEvents
    }

  final case class LoopState(
    // TODO: Static data shouldn't be in this case class
    eventType: EventType,
    expectedFocus: Int,
    leftElements: Int,
    rightElements: Int,
    // loop data
    loopFocus: Int,
    processingWindow: NonEmptyFilled[DetectionData],
    phase: FlightPhase,
  )

  final case class LoopResult(
    emitEvents: Vector[FlightEvents],
    remainingData: NonEmptyFilling[DetectionData],
    nextPhase: FlightPhase,
    nextFocus: Int,
  )

  // TODO: There is a lot of logic here, consider restructuring this work into its own object
  @tailrec
  private def loop(state: LoopState): Option[LoopResult] =
    if state.loopFocus > state.expectedFocus then
      return None

    val stageHandler = StageHandler(config, state.eventType)
    val stageConfig  = config.getEventConfigs(state.eventType)

    val focusedWindow =
      state
        .processingWindow
        .subwindowAt(state.leftElements, state.loopFocus, state.rightElements)

    val detectionResult =
      stageHandler
        .stageDetection(focusedWindow)
        .map {
          case (focusedWindowDetectedIdx, detectedPoint) =>
            val (beforeDetection, afterDetection) = focusedWindow.dequeue(focusedWindowDetectedIdx)
            val emitEvents                        = beforeDetection.map(_.detectedEvents)

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
        loop(state.focus(_.loopFocus).modify(_ + 1))
    }

  //  Detection Data  //

  private def buildCurrentDetectionData(
    previousWindow: NEW[DetectionData],
    currentPoint: IndexedDataPoint,
  ): DetectionData =
    val smoothingWindowSize = config.freefall.smoothingWindowSize

    val smoothingWindow =
      previousWindow
        .takeNewest(smoothingWindowSize)
        .map(_.point)

    val kinematics             = Kinematics.computeSmoothed(smoothingWindow, currentPoint)
    val previousPhaseEvents    = previousWindow.oldest.phase
    val previousDetectedEvents = previousWindow.oldest.detectedEvents

    DetectionData(
      point = currentPoint,
      kinematics = kinematics,
      phase = previousPhaseEvents,
      detectedEvents = previousDetectedEvents,
    )

}

object FlightStagesDetection {

  //  Factory  //

  def apply(config: DetectionConfig): FlightStagesDetection =
    new FlightStagesDetection(config)

  //  Type Class Instances  //

  given splitter[A: FileFormatAdapter]: Splitter[A, DataPoint, A] with
    def split(a: A): (DataPoint, A) =
      (FileFormatAdapter[A].toDataPoint(a), a)

  given combiner[A]: Combiner[FlightEvents, A, OutputFlightPoint[A]] with
    def combine(events: FlightEvents, source: A): OutputFlightPoint[A] =
      OutputFlightPoint(events.takeoff, events.freefall, events.canopy, events.landing, source)

}
