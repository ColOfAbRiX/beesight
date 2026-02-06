package com.colofabrix.scala.beesight.detection

import cats.data.State
import com.colofabrix.scala.beesight.collections.SlidingWindow
import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.utils.*
import com.colofabrix.scala.beesight.model.formats.FileFormatAdapter

/**
 * Detects flight stages (takeoff, freefall, canopy, landing) from flight data streams.
 */
final class FlightStagesDetection private (config: DetectionConfig) {

  private type StreamState[A] = State[DetectionState, A]

  final case class DetectionState(
    fullWindow: SlidingWindow[DetectionData],
    focus: Int,
  )

  final case class DetectionData(
    point: DataPoint,
    phase: FlightPhase,
    detectedEvents: DetectedEvents,
  )

  /**
   * Streaming pipe that detects flight stages using the provided configuration.
   */
  @scala.annotation.nowarn
  def streamDetect[F[_], A: FileFormatAdapter]: fs2.Pipe[F, A, OutputFlightPoint[A]] =
    stream =>
      stream
        .map { source =>
          InputFlightPoint(FileFormatAdapter[A].toDataPoint(source), source)
        }
        .zipWithIndex
        .mapStateCollect1(initialStreamState) {
          case (InputFlightPoint(source, currentPoint), idx) =>
            ???
        }

      stream.map { a =>
        OutputFlightPoint(
          takeoff = None,
          freefall = None,
          canopy = None,
          landing = None,
          source = a,
        )
      }

  //  Initialization  //

  private def initialStreamState[A](first: InputFlightPoint[A], idx: Long): (DetectionState, None.type) =
    val firstData =
      DetectionData(
        point = first.point,
        phase = FlightPhase.BeforeTakeoff,
        detectedEvents = DetectedEvents.empty,
      )
    DetectionState(
      fullWindow = SlidingWindow(slidingWindowSize).enqueue(firstData),
      focus = idx.toInt,
    ) -> None

  private lazy val slidingWindowSize: Int =
    val backWindows =
      List(
        config.takeoff.backtrackWindowSize,
        config.freefall.backtrackWindowSize,
        config.canopy.backtrackWindowSize,
        config.landing.backtrackWindowSize,
      )

    val validationWindows =
      List(
        config.takeoff.validationWindowSize,
        config.freefall.validationWindowSize,
        config.canopy.validationWindowSize,
        config.landing.validationWindowSize,
      )

    backWindows.max + 1 + validationWindows.max

}

object FlightStagesDetection {

  def apply(config: DetectionConfig): FlightStagesDetection =
    new FlightStagesDetection(config)

}
