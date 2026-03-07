package com.colofabrix.scala.beesight.detection

import cats.data.*
import cats.syntax.all.*
import com.colofabrix.scala.beesight.collections.*
import com.colofabrix.scala.beesight.collections.NEW.*
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
import scala.annotation.tailrec

/**
 * Detects flight stages from flight data streams.
 */
final class FlightStagesDetection private (config: DetectionConfig) {

  //  Configuration / Derived Values  //

  private val slidingWindowSize: Int =
    val smoothWindows     = config.eventConfigsAsList.map(_.smoothingWindowSize)
    val backWindows       = config.eventConfigsAsList.map(_.backtrackWindowSize)
    val validationWindows = config.eventConfigsAsList.map(_.validationWindowSize)
    Math.max(smoothWindows.max, backWindows.max) + 1 + validationWindows.max

  //  Dependencies  //

  private val preprocessor: PreprocessData =
    PreprocessData(config)

  private val phaseDetector: PhaseDetector =
    PhaseDetector(config)

  //  Public API  //

  /**
   * Streaming pipe that detects flight stages using the provided configuration.
   */
  def streamDetect[F[_], A: FileFormatAdapter]: fs2.Pipe[F, A, OutputFlightPoint[A]] =
    _.processBypass(mainDetectionPipe)

  //  Stream Pipeline  //

  private def mainDetectionPipe[F[_]]: fs2.Pipe[F, DataPoint, FlightEvents] =
    pointStream =>
      pointStream
        .through(applyIndex)
        .through(preprocessor.preprocess)
        .statefulMapEmits(initialStreamState, drainState) {
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
        DetectionState(window, FlightPhase.BeforeTakeoff, FocusPosition.Zero) -> Vector.empty
      case window: NonEmptyFilling[?] =>
        FillingState(window, FlightPhase.BeforeTakeoff, FocusPosition.Zero) -> Vector.empty
    }

  private def drainState(state: StateTrait): Vector[FlightEvents] =
    @tailrec
    def loop(state: DetectionState, iteration: Int, result: Vector[FlightEvents]): Vector[FlightEvents] =
      val (nextState, beforeDetectionEmits) =
        phaseDetector
          .detect()
          .run(state)
          .value

      nextState match {
        case ns: FillingState =>
          loop(ns.toDetectionState, iteration + 1, result ++ beforeDetectionEmits)
        case ns: DetectionState =>
          val (emitOldest, maybeShrunkWindow) = ns.window.shrinkReturning(1)
          maybeShrunkWindow match {
            case None =>
              result ++ emitOldest.map(_.detectedEvents)
            case Some(shrunkWindow) =>
              val emittedEvents = emitOldest.map(_.detectedEvents)
              val nextState     = ns.copy(window = shrunkWindow)
              loop(nextState, iteration + 1, result ++ emittedEvents)
          }
      }

    state match {
      case s: FillingState   => loop(s.toDetectionState, 1, Vector.empty)
      case s: DetectionState => loop(s, 1, Vector.empty)
    }

  //  State Handlers  //

  private def handleFillingState(currentPoint: IndexedDataPoint): Transition[FillingState, Unit] =
    IndexedState.modify { previousState =>
      val currentData = buildCurrentDetectionData(previousState.window, currentPoint)
      val window      = previousState.window.enqueue(currentData)

      window match {
        case currentWindow: NonEmptyFilling[?] =>
          FillingState(currentWindow, previousState.phase, previousState.focusType)
        case currentWindow: NonEmptyFilled[?] =>
          DetectionState(currentWindow, previousState.phase, previousState.focusType)
      }
    }

  private def handleDetectionState(currentPoint: IndexedDataPoint): Transition[DetectionState, Vector[FlightEvents]] =
    for {
      oldestData      <- pushCurrentDetectionState(currentPoint)
      additionalEmits <- phaseDetector.detect()
      allToEmit        = oldestData.detectedEvents +: additionalEmits
    } yield allToEmit

  //  State Operations  //

  private def pushCurrentDetectionState(currentPoint: IndexedDataPoint): KeepState[DetectionState, DetectionData] =
    IndexedState { previousState =>
      val currentData                 = buildCurrentDetectionData(previousState.window, currentPoint)
      val (oldestData, currentWindow) = previousState.window.pushReturning(currentData)
      val currentState                = previousState.copy(window = currentWindow)
      currentState -> oldestData
    }

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
