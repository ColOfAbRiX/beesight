package com.colofabrix.scala.beesight.detection

import cats.implicits.given
import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.model.*

/**
 * Detects and analyses different stages of a flight based on time-series data.
 *
 * The detection pipeline follows these stages:
 * 1. Preprocessing: Clean raw data (clip implausible spikes)
 * 2. Kinematics computation: Calculate derived values (smoothed speed, acceleration)
 * 3. Windows update: Update sliding windows for various calculations
 * 4. Phase detection: Detect flight phase and record stage points
 * 5. State assembly: Build new stream state
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
            Some(detect(i, dataPoint, prevState))
        }
        .collect {
          case Some(state) =>
            OutputFlightRow(
              phase = state.detectedPhase,
              takeoff = state.detectedStages.takeoff,
              freefall = state.detectedStages.freefall,
              canopy = state.detectedStages.canopy,
              landing = state.detectedStages.landing,
              lastPoint = state.detectedStages.lastPoint,
              isValid = state.detectedStages.isValid,
              source = state.inputPoint.source,
            )
        }

  private def detect[A](i: Long, dataPoint: InputFlightRow[A], prevState: StreamState[A]): StreamState[A] =
    //  Stage 1: Preprocessing  //
    val preprocessedPoint = Preprocessing.preprocess(dataPoint, prevState, config)

    //  Stage 2: Compute kinematics //
    val kinematics = Kinematics.compute(preprocessedPoint, prevState)

    //  Stage 3: Update windows  //
    val windows = Windows.update(prevState.windows, kinematics, i)

    //  Stage 5: Phase detection  //
    val currentPoint = FlightPoint(i, preprocessedPoint.altitude)

    // Create intermediate state with updated kinematics and windows for detection
    val intermediateState =
      prevState.copy(
        inputPoint = preprocessedPoint,
        dataPointIndex = i,
        kinematics = kinematics,
        windows = windows,
      )

    val detectionResult = PhaseDetection.detectAll(intermediateState, currentPoint, config)

    //  Stage 6: Build new state  //
    val takeoffMissing = TakeoffDetection.checkMissedTakeoff(prevState, preprocessedPoint, i, config)

    StreamState(
      inputPoint = preprocessedPoint,
      dataPointIndex = i,
      kinematics = kinematics,
      windows = windows,
      detectedPhase = detectionResult.phase,
      detectedStages = detectionResult.stages,
      takeoffMissing = takeoffMissing,
    )

}
