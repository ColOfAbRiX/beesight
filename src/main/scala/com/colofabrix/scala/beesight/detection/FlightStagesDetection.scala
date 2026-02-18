package com.colofabrix.scala.beesight.detection

import cats.data.*
import cats.Eval
import cats.syntax.all.*
import com.colofabrix.scala.beesight.collections.*
import com.colofabrix.scala.beesight.collections.NonEmptyWindow.*
import com.colofabrix.scala.beesight.config.{ DetectionConfig, EventConfigs }
import com.colofabrix.scala.beesight.detection.FlightStagesDetection.*
import com.colofabrix.scala.beesight.detection.FlightStagesDetection.given
import com.colofabrix.scala.beesight.detection.math.*
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.detection.preprocess.PreprocessData
import com.colofabrix.scala.beesight.detection.stages.*
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.model.formats.FileFormatAdapter
import com.colofabrix.scala.beesight.utils.*
import com.colofabrix.scala.beesight.utils.StreamBypass.*
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

  private val freefallHandler: StageHandler =
    FreefallHandler(config.freefall)

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
        .through(preprocessor.preprocess)
        .zipWithIndex
        .statefulMapEmits(initialStreamState.tupled) {
          case ((currentPoint, _), state: FillingState) =>
            handleFillingState(currentPoint)
              .as(Vector.empty[FlightEvents])
              .run(state)
              .value
          case ((currentPoint, idx), state: DetectionState) =>
            handleDetectionState(currentPoint, idx)
              .run(state)
              .value
        }

  private val initialStreamState: (DataPoint, Long) => (StateTrait, Vector[FlightEvents]) =
    case (first, _) =>
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
          phase = None,
          detectedEvents = FlightEvents.empty,
        )

      NEW(slidingWindowSize, firstData) match {
        case window: NonEmptyFilled[?] =>
          DetectionState(window, FlightPhase.BeforeTakeoff, 0) -> Vector.empty
        case window: NonEmptyFilling[?] =>
          FillingState(window, FlightPhase.BeforeTakeoff, 0) -> Vector.empty
      }

  //  State Handlers  //

  private def handleFillingState(currentPoint: DataPoint): Transition[FillingState, Unit] =
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

  private def handleDetectionState(
    currentPoint: DataPoint,
    idx: Long,
  ): Transition[DetectionState, Vector[FlightEvents]] =
    for {
      oldestData      <- pushCurrentDetectionState(currentPoint)
      additionalEmits <- detectPhase(idx)
      allToEmit        = oldestData.detectedEvents +: additionalEmits
    } yield allToEmit

  //  State Operations  //

  private def pushCurrentDetectionState(currentPoint: DataPoint): KeepState[DetectionState, DetectionData] =
    IndexedState { previousState =>
      val currentData                 = buildCurrentDetectionData(previousState.window, currentPoint)
      val (oldestData, currentWindow) = previousState.window.pushReturning(currentData)
      val currentState                = previousState.focus(_.window).replace(currentWindow)
      currentState -> oldestData
    }

  private def detectPhase(idx: Long): Transition[DetectionState, Vector[FlightEvents]] =
    IndexedState {
      case state @ DetectionState(_, FlightPhase.BeforeTakeoff, _) =>
        runDetectionOnWindow(state, freefallHandler, idx, EventType.Freefall)
          .getOrElse(state -> Vector.empty)
      case s @ DetectionState(window, FlightPhase.Climbing, focusIdx) =>
        s -> Vector.empty
      case s @ DetectionState(window, FlightPhase.Freefall, focusIdx) =>
        s -> Vector.empty
      case s @ DetectionState(window, FlightPhase.UnderCanopy, focusIdx) =>
        s -> Vector.empty
      case s @ DetectionState(window, FlightPhase.Landed, focusIdx) =>
        s -> Vector.empty
    }

  private def runDetectionOnWindow(
    currentState: DetectionState,
    stageHandler: StageHandler,
    oldestDataIdx: Long,
    eventType: EventType,
  ): Option[(StateTrait, Vector[FlightEvents])] =
    type DetectionType = Option[(StateTrait, Vector[FlightEvents])]

    val stageConfig = config.getEventConfigs(eventType)

    val leftElements  = Math.max(stageConfig.backtrackWindowSize, stageConfig.smoothingWindowSize)
    val rightElements = stageConfig.validationWindowSize
    val expectedFocus = leftElements

    @tailrec
    def loop(loopCurrentFocus: Int, accumulator: DetectionType): DetectionType =
      val focusedWindow =
        currentState
          .window
          .subwindowAt(leftElements, loopCurrentFocus, rightElements)

      val loopResult =
        stageHandler
          .stageDetection(focusedWindow)
          .map { detectionIdx =>
            val (beforeDetection, inclusiveAfterDetection) = focusedWindow.dequeue(detectionIdx - 1)

            // TODO: Check that these past events have the correct `detectedEvents`. How was it set?
            val beforeDetectionEvents = beforeDetection.map(_.detectedEvents)

            val newWindow =
              inclusiveAfterDetection.map { dataPoint =>
                dataPoint
                  .focus(_.phase)
                  .modify(identity)                                        // TODO: Update with correct value
                  .focus(_.detectedEvents.freefall)                        // TODO: This must changed based on eventType
                  .set(FlightEventPoint(0, dataPoint.point.altitude).some) /* TODO: Index should be included with data
                   * point */
              }

            FillingState(newWindow, FlightPhase.Freefall, detectionIdx) -> beforeDetection
          }

      if loopCurrentFocus <= expectedFocus then
        loop(loopCurrentFocus + 1, loopResult)
      else
        accumulator

    loop(currentWindowFocus, None)

  //  Pure Helpers  //

  private def buildCurrentDetectionData(previousWindow: NEW[DetectionData], currentPoint: DataPoint): DetectionData =
    val smoothingWindowSize = config.freefall.smoothingWindowSize

    val smoothingWindow =
      previousWindow
        .takeNewest(smoothingWindowSize)
        .map(_.point)

    val kinematics = Kinematics.computeSmoothed(smoothingWindow, currentPoint)

    DetectionData(currentPoint, kinematics, None, FlightEvents.empty)

}

object FlightStagesDetection {

  //  Factory  //

  def apply(config: DetectionConfig): FlightStagesDetection =
    new FlightStagesDetection(config)

  //  Extensions  //

  extension (self: DetectionConfig) {

    def getEventConfigs(eventType: EventType): EventConfigs =
      eventType match {
        case EventType.Takeoff  => self.takeoff
        case EventType.Freefall => self.freefall
        case EventType.Canopy   => self.canopy
        case EventType.Landing  => self.landing
      }

    def eventConfigsAsList: Vector[EventConfigs] =
      Vector(self.takeoff, self.freefall, self.canopy, self.landing)

  }

  //  Type Class Instances  //

  given splitter[A: FileFormatAdapter]: Splitter[A, DataPoint, A] with
    def split(a: A): (DataPoint, A) =
      (FileFormatAdapter[A].toDataPoint(a), a)

  given combiner[A]: Combiner[FlightEvents, A, OutputFlightPoint[A]] with
    def combine(events: FlightEvents, source: A): OutputFlightPoint[A] =
      OutputFlightPoint(events.takeoff, events.freefall, events.canopy, events.landing, source)

}
