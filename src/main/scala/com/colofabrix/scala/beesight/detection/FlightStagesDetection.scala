package com.colofabrix.scala.beesight.detection

import cats.data.State
import cats.syntax.all.*
import com.colofabrix.scala.beesight.collections.*
import com.colofabrix.scala.beesight.collections.NonEmptyWindow.*
import com.colofabrix.scala.beesight.config.{ DetectionConfig, EventConfigs }
import com.colofabrix.scala.beesight.detection.FlightStagesDetection.*
import com.colofabrix.scala.beesight.detection.math.*
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.detection.preprocess.PreprocessData
import com.colofabrix.scala.beesight.detection.stages.*
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.model.formats.FileFormatAdapter
import com.colofabrix.scala.beesight.utils.*
import monocle.Iso
import monocle.syntax.all.*
import scala.annotation.nowarn

/**
 * Detects flight stages (takeoff, freefall, canopy, landing) from flight data streams.
 */
final class FlightStagesDetection private (config: DetectionConfig) {

  private val preprocessor: PreprocessData =
    PreprocessData(config)

  private def freefallHandler: FreefallHandler =
    FreefallHandler(config)

  /**
   * Streaming pipe that detects flight stages using the provided configuration.
   */
  def streamDetect[F[_], A: FileFormatAdapter]: fs2.Pipe[F, A, OutputFlightPoint[A]] =
    rawStream =>
      rawStream
        .map { rawPoint =>
          (dataPoint = FileFormatAdapter[A].toDataPoint(rawPoint), source = rawPoint)
        }
        .through { rawPointStream =>
          mainDetectionPipe(rawPointStream.map(_.dataPoint))
            .zip(rawPointStream.map(_.source))
            .map {
              case (flightEvents, rawPoint) =>
                OutputFlightPoint(
                  takeoff = flightEvents.takeoff,
                  freefall = flightEvents.freefall,
                  canopy = flightEvents.canopy,
                  landing = flightEvents.landing,
                  source = rawPoint,
                )
            }
        }

  private def mainDetectionPipe[F[_], A]: fs2.Pipe[F, DataPoint, FlightEvents] =
    stream =>
      stream
        .through(preprocessor.preprocess)
        .take(100) // FIXME: Just for debugging
        .zipWithIndex
        .mapStateCollect1(initialStreamState) {
          case (currentPoint, idx) =>
            for {
              oldestData <- pushCurrentPoint(currentPoint)
              _          <- transitionToRunning()
              phase      <- detectPhase(currentPoint, idx)
            } yield oldestData.flatMap(_.detectedEvents)
        }

  private def pushCurrentPoint(currentPoint: DataPoint): StreamState[Option[DetectionData]] =
    State {
      case previousState: DetectionState.Filling =>
        val kinematics         = Kinematics.compute(previousState.window.newest.point, currentPoint)
        val currentData        = DetectionData(currentPoint, kinematics, None, None)
        val (_, currentWindow) = previousState.window.push(currentData)
        val currentState       = previousState.focus(_.window).replace(currentWindow)
        (currentState, None)
      case previousState: DetectionState.Running =>
        val kinematics                   = Kinematics.compute(previousState.window.newest.point, currentPoint)
        val currentData                  = DetectionData(currentPoint, kinematics, None, None)
        val (oldestPoint, currentWindow) = previousState.window.pushReturning(currentData)
        val currentState                 = previousState.focus(_.window).replace(currentWindow)
        (currentState, Some(oldestPoint))
    }

  private def computeSmoothedKinematics(
    currentPoint: DataPoint,
    previousWindow: NonEmptyWindow[DataPoint],
    eventType: EventType,
  ): PointKinematics =
    ???
    // val smoothingWindowSize = config.getSmoothingWindowSize(eventType)
    // val smoothingWindow     = window.toVector.takeRight(smoothingWindowSize)
    // Kinematics.computeSmoothed(currentPoint, smoothingWindow)

  private def transitionToRunning(): StreamState[Unit] =
    DetectionState.fillingModify { state =>
      state
        .window
        .asFilledWindow
        .fold(state) { filledWindow =>
          DetectionState.Running(
            window = filledWindow,
            phase = FlightPhase.BeforeTakeoff,
            focus = config.getWindowFocus(EventType.Takeoff),
          )
        }
    }

  private def detectPhase(currentPoint: DataPoint, idx: Long): StreamState[Option[FlightPhase]] =
    DetectionState.runningState { state =>
      state.phase match {
        case FlightPhase.BeforeTakeoff =>
          println(currentPoint.toString + idx.toString)
          val trigger = freefallHandler.detectTrigger(state.window.focusAt(state.focus))
          val result  = (state, state.phase)
          result
        case FlightPhase.Climbing    => (state, state.phase)
        case FlightPhase.Freefall    => (state, state.phase)
        case FlightPhase.UnderCanopy => (state, state.phase)
        case FlightPhase.Landed      => (state, state.phase)
      }
    }

  //  Initialization  //

  @nowarn
  private def initialStreamState(first: DataPoint, idx: Long): (DetectionState, None.type) =
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
        detectedEvents = None,
      )

    val firstWindow = NonEmptyWindow(slidingWindowSize, firstData)

    DetectionState.Filling(firstWindow) -> None

  private val slidingWindowSize: Int =
    val smoothWindows     = config.eventConfigsAsList.map(_.smoothingWindowSize)
    val backWindows       = config.eventConfigsAsList.map(_.backtrackWindowSize)
    val validationWindows = config.eventConfigsAsList.map(_.validationWindowSize)
    Math.max(smoothWindows.max, backWindows.max) + 1 + validationWindows.max

}

object FlightStagesDetection {

  private type StreamState[A] =
    State[DetectionState, A]

  def apply(config: DetectionConfig): FlightStagesDetection =
    new FlightStagesDetection(config)

  extension (self: DetectionConfig) {

    def eventConfigsAsList: List[EventConfigs] =
      List(self.takeoff, self.freefall, self.canopy, self.landing)

    def getWindowFocus(eventType: EventType): Int =
      eventType match {
        case EventType.Takeoff  => Math.max(self.takeoff.backtrackWindowSize, self.takeoff.smoothingWindowSize)
        case EventType.Freefall => Math.max(self.freefall.backtrackWindowSize, self.freefall.smoothingWindowSize)
        case EventType.Canopy   => Math.max(self.canopy.backtrackWindowSize, self.canopy.smoothingWindowSize)
        case EventType.Landing  => Math.max(self.landing.backtrackWindowSize, self.landing.smoothingWindowSize)
      }

    def getSmoothingWindowSize(eventType: EventType): Int =
      eventType match {
        case EventType.Takeoff  => self.takeoff.smoothingWindowSize
        case EventType.Freefall => self.freefall.smoothingWindowSize
        case EventType.Canopy   => self.canopy.smoothingWindowSize
        case EventType.Landing  => self.landing.smoothingWindowSize
      }

  }

  given splitIso[A: FileFormatAdapter]: Iso[A, (DataPoint, A)] =
    Iso((a: A) => (FileFormatAdapter[A].toDataPoint(a), a))(_._2)

  given combineIso[A]: Iso[OutputFlightPoint[A], (FlightEvents, A)] =
    Iso { (o: OutputFlightPoint[A]) =>
      (FlightEvents(o.takeoff, o.freefall, o.canopy, o.landing), o.source)
    } {
      case (events, source) =>
        OutputFlightPoint(events.takeoff, events.freefall, events.canopy, events.landing, source)
    }

}
