package com.colofabrix.scala.beesight.detection

import cats.data.State
import cats.syntax.all.*
import com.colofabrix.scala.beesight.collections.NESlidingWindow
import com.colofabrix.scala.beesight.collections.NESlidingWindow.*
import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.config.EventConfigs
import com.colofabrix.scala.beesight.detection.FlightStagesDetection.*
import com.colofabrix.scala.beesight.detection.math.Kinematics
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.detection.preprocess.PreprocessData
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.model.formats.FileFormatAdapter
import com.colofabrix.scala.beesight.utils.*
import monocle.syntax.all.*
import scala.annotation.nowarn

/**
 * Detects flight stages (takeoff, freefall, canopy, landing) from flight data streams.
 */
final class FlightStagesDetection private (config: DetectionConfig) {

  private def preprocessor[A]: PreprocessData[A] =
    PreprocessData[A](config)

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
              _          <- transitionState()
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

  private def transitionState(): StreamState[Unit] =
    State.modifyFilling { state =>
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

  @nowarn
  private def detectPhase(currentPoint: DataPoint, idx: Long): StreamState[Option[FlightPhase]] =
    State.running { state =>
      state.phase match {
        case FlightPhase.BeforeTakeoff => (state, state.phase)
        case FlightPhase.Climbing      => (state, state.phase)
        case FlightPhase.Freefall      => (state, state.phase)
        case FlightPhase.UnderCanopy   => (state, state.phase)
        case FlightPhase.Landed        => (state, state.phase)
      }
    }

  //  Initialization  //

  @nowarn
  private def initialStreamState(first: DataPoint, idx: Long): (DetectionState, None.type) =
    val backWindows       = config.eventConfigsAsList.map(_.backtrackWindowSize)
    val validationWindows = config.eventConfigsAsList.map(_.validationWindowSize)
    val slidingWindowSize = backWindows.max + 1 + validationWindows.max

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

    val firstWindow = NESlidingWindow(slidingWindowSize, firstData)

    DetectionState.Filling(firstWindow) -> None

}

object FlightStagesDetection {

  private type StreamState[A] = State[DetectionState, A]

  enum DetectionState {

    case Filling(
      window: NESlidingWindow[DetectionData],
    ) extends DetectionState

    case Running(
      window: NESlidingWindow.NEFilledWindow[DetectionData],
      phase: FlightPhase,
      focus: Int,
    ) extends DetectionState

  }

  final case class DetectionData(
    point: DataPoint,
    kinematics: PointKinematics,
    phase: Option[FlightPhase],
    detectedEvents: Option[FlightEvents],
  )

  def apply(config: DetectionConfig): FlightStagesDetection =
    new FlightStagesDetection(config)

  extension (self: DetectionConfig) {

    def eventConfigsAsList: List[EventConfigs] =
      List(self.takeoff, self.freefall, self.canopy, self.landing)

    def getWindowFocus(eventType: EventType): Int =
      eventType match {
        case EventType.Takeoff  => self.takeoff.backtrackWindowSize
        case EventType.Freefall => self.freefall.backtrackWindowSize
        case EventType.Canopy   => self.canopy.backtrackWindowSize
        case EventType.Landing  => self.landing.backtrackWindowSize
      }

  }

  extension (self: State.type) {

    def modifyFilling(f: DetectionState.Filling => DetectionState): StreamState[Unit] =
      State.modify {
        case state: DetectionState.Filling => f(state)
        case state: DetectionState.Running => state
      }

    def running[A](f: DetectionState.Running => (DetectionState.Running, A)): StreamState[Option[A]] =
      State.modify {
        case state: DetectionState.Filling => (state, None)
        case state: DetectionState.Running => f(state).bimap(identity, Some(_))
      }

  }

}
