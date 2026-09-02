package com.colofabrix.scala.beesight.jumpevents

import cats.data.*
import cats.syntax.all.*
import com.colofabrix.scala.beesight.collections.*
import com.colofabrix.scala.beesight.collections.NEW.*
import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.jumpevents.model.{ DetectingState, DetectorState, FillingState, FocusPosition, Step, Transition, WindowEntry }
import com.colofabrix.scala.beesight.jumpevents.model.DetectorConfigActions.*
import com.colofabrix.scala.beesight.math.*
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.preprocess.PreprocessData
import com.colofabrix.scala.beesight.utils.*
import java.time.Duration as JavaDuration
import scala.annotation.tailrec

/**
 * Detects flight event points from flight data streams.
 */
final class JumpEventsDetector private (config: DetectionConfig) {

  //  Configuration / Derived Values  //

  private val slidingWindowSize: Int =
    val smoothWindows     = config.eventConfigsAsList.map(_.smoothingWindowSize.value)
    val backWindows       = config.eventConfigsAsList.map(_.backtrackWindowSize.value)
    val validationWindows = config.eventConfigsAsList.map(_.validationWindowSize.value)
    Math.max(smoothWindows.max, backWindows.max) + 1 + validationWindows.max

  //  Dependencies  //

  private val preprocessor: PreprocessData =
    PreprocessData(config)

  private val eventPointDetector: EventPointDetector =
    EventPointDetector(config)

  //  Public API  //

  /**
   * Streaming pipe that detects flight event points using the provided configuration.
   */
  def streamDetect[F[_], A: PointReader]: fs2.Pipe[F, A, FlightEvents] =
    _.through(mainDetectionPipe)

  //  Internals  //

  private def mainDetectionPipe[F[_], A: PointReader]: fs2.Pipe[F, A, FlightEvents] =
    pointStream =>
      pointStream
        .through(preprocessor.preprocess)
        .through(IndexedPoint.stream)
        .statefulMapEmits(initialStreamState, drainState) {
          case (currentPoint, state: FillingState) =>
            handleFilling(currentPoint)
              .as(Vector.empty[FlightEvents])
              .run(state)
              .value
          case (currentPoint, state: DetectingState) =>
            handleDetecting(currentPoint)
              .run(state)
              .value
        }

  private def initialStreamState(first: IndexedPoint): (DetectorState, Vector[FlightEvents]) =
    val firstKinematics =
      PointKinematics(
        point = DataPoint.fromReader(first),
        acceleration = GeoVector.zero,
      )

    val firstData =
      WindowEntry(
        point = first,
        kinematics = firstKinematics,
        phase = FlightPhase.BeforeTakeoff,
        detectedEvents = FlightEvents.empty,
      )

    NEW(slidingWindowSize, firstData) match {
      case window: NonEmptyFilled[?] =>
        DetectingState(window, FlightPhase.BeforeTakeoff, FocusPosition.Zero) -> Vector.empty
      case window: NonEmptyFilling[?] =>
        FillingState(window, FlightPhase.BeforeTakeoff, FocusPosition.Zero) -> Vector.empty
    }

  private def drainState(state: DetectorState): Vector[FlightEvents] =
    @tailrec
    def loop(state: DetectingState, iteration: Int, result: Vector[FlightEvents]): Vector[FlightEvents] =
      val (nextState, beforeDetectionEmits) =
        eventPointDetector
          .detect()
          .run(state)
          .value

      nextState match {
        case ns: FillingState =>
          loop(ns.toDetecting, iteration + 1, result ++ beforeDetectionEmits)
        case ns: DetectingState =>
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
      case s: FillingState   => loop(s.toDetecting, 1, Vector.empty)
      case s: DetectingState => loop(s, 1, Vector.empty)
    }

  //  State Handlers  //

  private def handleFilling(currentPoint: IndexedPoint): Transition[FillingState, Unit] =
    IndexedState.modify { previousState =>
      val currentData = buildCurrentWindowEntry(previousState.window, currentPoint)
      val window      = previousState.window.enqueue(currentData)

      window match {
        case currentWindow: NonEmptyFilling[?] =>
          FillingState(currentWindow, previousState.phase, previousState.focusType)
        case currentWindow: NonEmptyFilled[?] =>
          DetectingState(currentWindow, previousState.phase, previousState.focusType)
      }
    }

  private def handleDetecting(currentPoint: IndexedPoint): Transition[DetectingState, Vector[FlightEvents]] =
    for {
      oldestData      <- pushCurrentWindowEntry(currentPoint)
      additionalEmits <- eventPointDetector.detect()
      allToEmit        = oldestData.detectedEvents +: additionalEmits
    } yield allToEmit

  //  State Operations  //

  private def pushCurrentWindowEntry(currentPoint: IndexedPoint): Step[DetectingState, WindowEntry] =
    IndexedState { previousState =>
      val currentData                 = buildCurrentWindowEntry(previousState.window, currentPoint)
      val (oldestData, currentWindow) = previousState.window.pushReturning(currentData)
      val currentState                = previousState.copy(window = currentWindow)
      currentState -> oldestData
    }

  private def buildCurrentWindowEntry(
    previousWindow: NEW[WindowEntry],
    currentPoint: IndexedPoint,
  ): WindowEntry =
    val smoothingWindowSize = config.freefall.smoothingWindowSize
    val smoothingCap        = Math.min(smoothingWindowSize.value, previousWindow.length)
    val previousPoint       = previousWindow.newest.point

    val dt = JavaDuration.between(previousPoint.time, currentPoint.time).toMillis / 1000.0

    val currentSpeed =
      if smoothingCap <= 1 then
        currentPoint.speed
      else
        Stats.medianGeoVectorWithExtra(previousWindow, smoothingCap - 1, currentPoint.speed)(_.point.speed)

    val currentAcceleration = (currentPoint.speed - previousPoint.speed) / dt

    val kinematics =
      PointKinematics(
        point = DataPoint(
          time = currentPoint.time,
          altitude = currentPoint.altitude,
          speed = currentSpeed
        ),
        acceleration = currentAcceleration,
      )

    val previousPhaseEvents    = previousWindow.oldest.phase
    val previousDetectedEvents = previousWindow.oldest.detectedEvents

    WindowEntry(
      point = currentPoint,
      kinematics = kinematics,
      phase = previousPhaseEvents,
      detectedEvents = previousDetectedEvents,
    )

}

object JumpEventsDetector {

  def apply(config: DetectionConfig): JumpEventsDetector =
    new JumpEventsDetector(config)

}
