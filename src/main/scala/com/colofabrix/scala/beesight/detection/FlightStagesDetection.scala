package com.colofabrix.scala.beesight.detection

import cats.implicits.given
import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.detection.model.*

/**
 * Detects and analyses different stages of a flight based on time-series data.
 */
object FlightStagesDetection {

  private val config: DetectionConfig =
    DetectionConfig.default

  /**
   * Streaming detection
   */
  def streamDetectA[F[_], A](using A: FileFormatAdapter[A]): fs2.Pipe[F, A, OutputFlightRow[A]] =
    data =>
      data
        .map(A.toInputFlightPoint)
        .zipWithIndex
        .scan(Option.empty[StreamState[A]]) {
          case (None, (firstDataPoint, i)) =>
            Some(StreamState.create(firstDataPoint, config))
          case (Some(prevState), (dataPoint, i)) =>
            Some(processPoint(i, dataPoint, prevState))
        }
        .collect {
          case Some(state) => StreamState.toOutputFlightRow(state)
        }

  private def processPoint[A](i: Long, dataPoint: InputFlightRow[A], prevState: StreamState[A]): StreamState[A] =
    val preprocessedPoint = Preprocessing.process(dataPoint, prevState, config)

    val kinematics = Kinematics.compute(preprocessedPoint, prevState)
    val windows    = Windows.enqueue(prevState.windows, kinematics, i)

    val intermediateState =
      prevState.copy(
        inputPoint = preprocessedPoint,
        dataPointIndex = i,
        kinematics = kinematics,
        windows = windows,
      )

    val currentPoint    = FlightPoint(i, preprocessedPoint.altitude)
    val detectionResult = detect(intermediateState, currentPoint)

    StreamState(
      inputPoint = dataPoint,
      dataPointIndex = i,
      kinematics = kinematics,
      windows = windows,
      detectedPhase = detectionResult.currentPhase,
      detectedStages = detectionResult.events,
      takeoffMissing = detectionResult.missedTakeoff,
    )

  private def detect(state: StreamState[?], currentPoint: FlightPoint): DetectionResult =
    val detectionResult =
      state.detectedPhase match {
        case FlightPhase.BeforeTakeoff =>
          val tryFreefall = FreefallDetection.detect(state, currentPoint, config)
          val tryTakeoff  = TakeoffDetection.detect(state, currentPoint, config)
          tryFreefall orElse tryTakeoff

        case FlightPhase.Takeoff =>
          FreefallDetection.detect(state, currentPoint, config)

        case FlightPhase.Freefall =>
          CanopyDetection.detect(state, currentPoint, config)

        case FlightPhase.Canopy =>
          LandingDetection.detect(state, currentPoint, config)

        case FlightPhase.Landing =>
          None
      }

    val currentState =
      DetectionResult(
        currentPhase = state.detectedPhase,
        events = state.detectedStages.copy(lastPoint = state.dataPointIndex),
        missedTakeoff = state.takeoffMissing,
      )

    detectionResult.fold(currentState)(currentState |+| _)

}
