package com.colofabrix.scala.beesight.jumpevents

import cats.data.*
import cats.syntax.all.*
import com.colofabrix.scala.beesight.collections.*
import com.colofabrix.scala.beesight.collections.NEW.*
import com.colofabrix.scala.beesight.config.JumpEventsConfig
import com.colofabrix.scala.beesight.jumpevents.model.*
import com.colofabrix.scala.beesight.jumpevents.model.JumpEventsConfigActions.*
import com.colofabrix.scala.beesight.math.*
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.readers.PointReader
import com.colofabrix.scala.beesight.utils.*
import java.time.Duration as JavaDuration
import scala.annotation.tailrec

/**
 * Detects flight event points from flight data streams.
 */
final class JumpEventsDetector private (config: JumpEventsConfig) {

  //  Configuration / Derived Values  //

  private val slidingWindowSize: Int =
    val configList        = config.eventConfigsAsList
    val smoothWindows     = configList.map(_.smoothingWindowSize.value)
    val backWindows       = configList.map(_.backtrackWindowSize.value)
    val validationWindows = configList.map(_.validationWindowSize.value)
    Math.max(smoothWindows.max, backWindows.max) + 1 + validationWindows.max

  private val eventPointDetector: StagesHandler =
    StagesHandler(config)

  private val speedClipper: SpeedClipper =
    SpeedClipper(config)

  //  Public API  //

  /**
   * Streaming pipe that detects flight event points
   */
  def detect[F[_], A: PointReader]: fs2.Pipe[F, A, FlightEvents] =
    _.through(mainDetectionPipe)

  //  Internals  //

  private def mainDetectionPipe[F[_], A: PointReader]: fs2.Pipe[F, A, FlightEvents] =
    pointStream =>
      pointStream
        .through(IndexedPoint.stream)
        .through(speedClipper.clip)
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
    val firstData =
      WindowEntry(
        point = first,
        smoothedSpeed = first.speed,
        acceleration = GeoVector.zero,
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
      val currentData = buildCurrentWindowEntry(previousState.window, currentPoint, previousState.phase)
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
      val currentData                 = buildCurrentWindowEntry(previousState.window, currentPoint, previousState.phase)
      val (oldestData, currentWindow) = previousState.window.pushReturning(currentData)
      val currentState                = previousState.copy(window = currentWindow)
      currentState -> oldestData
    }

  private def buildCurrentWindowEntry(
    previousWindow: NEW[WindowEntry],
    currentPoint: IndexedPoint,
    phase: FlightPhase,
  ): WindowEntry =
    val smoothingWindowSize = config.smoothingWindowFor(phase)
    val smoothingCap        = Math.min(smoothingWindowSize.value, previousWindow.length)
    val previousPoint       = previousWindow.newest.point

    val dt = JavaDuration.between(previousPoint.time, currentPoint.time).toMillis / 1000.0

    val smoothedSpeed =
      if smoothingCap <= 1 then
        currentPoint.speed
      else
        previousWindow
          .takeNewest(smoothingCap)
          .map(_.point)
          .enqueue(currentPoint)
          .medianBy(_.speed)

    val acceleration = (currentPoint.speed - previousPoint.speed) / dt

    val previousPhaseEvents    = previousWindow.oldest.phase
    val previousDetectedEvents = previousWindow.oldest.detectedEvents

    WindowEntry(
      point = currentPoint,
      smoothedSpeed = smoothedSpeed,
      acceleration = acceleration,
      phase = previousPhaseEvents,
      detectedEvents = previousDetectedEvents,
    )

}

object JumpEventsDetector {

  def apply(config: JumpEventsConfig): JumpEventsDetector =
    new JumpEventsDetector(config)

}
