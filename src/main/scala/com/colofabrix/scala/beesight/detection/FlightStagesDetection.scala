package com.colofabrix.scala.beesight.detection

import cats.Eval
import cats.data.*
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

/**
 * Detects flight stages (takeoff, freefall, canopy, landing) from flight data streams.
 */
final class FlightStagesDetection private (config: DetectionConfig) {

  private type StateHandler[S, A] =
    IndexedState[S, StateTrait, A]

  private val preprocessor: PreprocessData =
    PreprocessData(config)

  private def freefallHandler: FreefallHandler =
    FreefallHandler(config)

  /**
   * Streaming pipe that detects flight stages using the provided configuration.
   */
  def streamDetect[F[_], A: FileFormatAdapter]: fs2.Pipe[F, A, OutputFlightPoint[A]] =
    rawStream => rawStream.processBypass(mainDetectionPipe)

  private def mainDetectionPipe[F[_]]: fs2.Pipe[F, DataPoint, FlightEvents] =
    pointStream =>
      pointStream
        .through(preprocessor.preprocess)
        .take(100) // FIXME: This is for debug only
        .zipWithIndex
        .mapStateCollect1(initialStreamState.tupled) { (currentPoint, idx) =>
          State {
            case state: FillingState =>
              handleFillingState(currentPoint).run(state).value
            case state: DetectionState =>
              handleDetectionState(currentPoint).run(state).value
          }
        }

  private def handleFillingState(currentPoint: DataPoint): StateHandler[FillingState, Option[FlightEvents]] =
    IndexedState { previousState =>
      val smoothingWindowSize = config.freefall.smoothingWindowSize

      val smoothingWindow =
        previousState
          .window
          .takeNewest(smoothingWindowSize)
          .map(_.point)

      val kinematics    = Kinematics.computeSmoothed(smoothingWindow, currentPoint)
      val currentData   = DetectionData(currentPoint, kinematics, None, None)
      val currentWindow = previousState.window.enqueue(currentData)

      currentWindow match {
        case currentFillingWindow: NonEmptyFilling[?] =>
          (FillingState(currentFillingWindow), None)
        case currentFilledWindow: NonEmptyFilled[?] =>
          (DetectionState(currentFilledWindow, FlightPhase.BeforeTakeoff), None)
      }
    }

  private def handleDetectionState(currentPoint: DataPoint): StateHandler[DetectionState, Option[FlightEvents]] =
    asStateHandler {
      for {
        _ <- pushRawDetectionState(currentPoint)
        _ <- detectPhase()
      } yield None
    }

  private def pushRawDetectionState(currentPoint: DataPoint): State[DetectionState, Unit] =
    State.modify { previousState =>
      val smoothingWindowSize = config.freefall.smoothingWindowSize

      val smoothingWindow =
        previousState
          .window
          .takeNewest(smoothingWindowSize)
          .map(_.point)

      val kinematics    = Kinematics.computeSmoothed(smoothingWindow, currentPoint)
      val currentData   = DetectionData(currentPoint, kinematics, None, None)
      val currentWindow = previousState.window.enqueue(currentData)

      DetectionState(currentWindow, FlightPhase.BeforeTakeoff)
    }

  private def detectPhase(): State[DetectionState, Unit] =
    State.pure(())

  private val initialStreamState: (DataPoint, Long) => (StateTrait, Option[FlightEvents]) =
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
          detectedEvents = None,
        )

      NEW(slidingWindowSize, firstData) match {
        case window: NonEmptyFilled[?] =>
          DetectionState(window, FlightPhase.BeforeTakeoff) -> None
        case window: NonEmptyFilling[?] =>
          FillingState(window) -> None
      }

  private val slidingWindowSize: Int =
    val smoothWindows     = config.eventConfigsAsList.map(_.smoothingWindowSize)
    val backWindows       = config.eventConfigsAsList.map(_.backtrackWindowSize)
    val validationWindows = config.eventConfigsAsList.map(_.validationWindowSize)
    Math.max(smoothWindows.max, backWindows.max) + 1 + validationWindows.max

}

object FlightStagesDetection {

  def apply(config: DetectionConfig): FlightStagesDetection =
    new FlightStagesDetection(config)

  extension (self: DetectionConfig) {

    def getEventConfigs(eventType: EventType): EventConfigs =
      eventType match {
        case EventType.Takeoff  => self.takeoff
        case EventType.Freefall => self.freefall
        case EventType.Canopy   => self.canopy
        case EventType.Landing  => self.landing
      }

    def eventConfigsAsList: List[EventConfigs] =
      List(self.takeoff, self.freefall, self.canopy, self.landing)

  }

  given splitter[A: FileFormatAdapter]: Splitter[A, DataPoint, A] with
    def split(a: A): (DataPoint, A) =
      (FileFormatAdapter[A].toDataPoint(a), a)

  given combiner[A]: Combiner[FlightEvents, A, OutputFlightPoint[A]] with
    def combine(events: FlightEvents, source: A): OutputFlightPoint[A] =
      OutputFlightPoint(events.takeoff, events.freefall, events.canopy, events.landing, source)

  private def asStateHandler[S <: StateTrait, A](state: State[S, A]): IndexedState[S, StateTrait, A] =
    state.modify[StateTrait](identity)

}
