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
import monocle.syntax.all.*

/**
 * Detects flight stages from flight data streams.
 */
final class FlightStagesDetection private (config: DetectionConfig) {

  //  Debug Logging  //

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
    debugSection(s"INIT: First point P${first.idx}") {
      debug(s"  Window size: $slidingWindowSize")
      debug(s"  Altitude: ${first.altitude}, Speed: ${first.speed}")
    }

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
        debug(s"  → Started as DetectionState")
        DetectionState(window, FlightPhase.BeforeTakeoff, 0) -> Vector.empty
      case window: NonEmptyFilling[?] =>
        debug(s"  → Started as FillingState (need ${slidingWindowSize - 1} more points)")
        FillingState(window, FlightPhase.BeforeTakeoff, 0) -> Vector.empty
    }

  //  State Handlers  //

  private def handleFillingState(currentPoint: IndexedDataPoint): Transition[FillingState, Unit] =
    IndexedState.modify { previousState =>
      debug(s"FILL: P${currentPoint.idx} → Window ${windowSummary(previousState.window)} (phase: ${previousState.phase})")

      val currentData = buildCurrentDetectionData(previousState.window, currentPoint)
      val window      = previousState.window.enqueue(currentData)

      window match {
        case currentWindow: NonEmptyFilling[?] =>
          debug(s"      Still filling: ${currentWindow.length}/${currentWindow.capacity}")
          FillingState(currentWindow, previousState.phase, previousState.focusIdx)
        case currentWindow: NonEmptyFilled[?] =>
          debug(s"      ★ Window FULL → DetectionState")
          DetectionState(currentWindow, previousState.phase, previousState.focusIdx)
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
      debugSection(s"DETECT: P${currentPoint.idx} arrives (phase: ${previousState.phase}, focusIdx: ${previousState.focusIdx})") {
        debug(s"  Window: ${windowSummary(previousState.window)}")
      }

      val currentData                 = buildCurrentDetectionData(previousState.window, currentPoint)
      val (oldestData, currentWindow) = previousState.window.pushReturning(currentData)
      val currentState                = previousState.focus(_.window).replace(currentWindow)

      debug(s"  Push: P${currentPoint.idx} in, P${oldestData.point.idx} out")
      debug(s"  Oldest events: ${eventsSummary(oldestData.detectedEvents)}")

      currentState -> oldestData
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
