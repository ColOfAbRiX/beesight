package com.colofabrix.scala.beesight.detection

import cats.data.Reader
import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.config.DetectionConfig.default.*
import com.colofabrix.scala.beesight.detection.FlightStagesDetection.*
import com.colofabrix.scala.beesight.model.*
import java.time.*

/**
 * Detects and analyses different stages of a flight based on time-series data
 */
object FlightStagesDetection {

  /**
   * Streaming detection - emits an OutputFlightPoint for each input
   */
  def streamDetectA[F[_], A](using A: FlightInfo[A]): fs2.Pipe[F, A, OutputFlightPoint[A]] = data =>
    data
      .map(A.toInputFlightPoint)
      .zipWithIndex
      .scan((Option.empty[StreamState[A]], FlightStagesPoints.empty)) {
        case ((None, detectedStages), (firstPoint, i)) =>
          // First data row - no preprocessing, initialize the state
          val initialState =
            StreamState.createDefault(
              firstPoint,
              SmoothingVerticalSpeedWindowSize,
              LandingStabilityWindowSize,
              BacktrackVerticalSpeedWindowSize,
            )
          val newStreamState       = updateState(initialState, firstPoint, i)
          val newFlightStagesPoint = updateFlightStagesPoints(detectedStages, newStreamState)
          (Some(newStreamState), newFlightStagesPoint)

        case ((Some(state), detectedStages), (rawPoint, i)) =>
          val point                = Preprocessing.preprocessData(rawPoint, state)
          val newStreamState       = updateState(state, point, i)
          val newFlightStagesPoint = updateFlightStagesPoints(detectedStages, newStreamState)
          (Some(newStreamState), newFlightStagesPoint)
      }
      .collect {
        case (Some(state), detectedStages) =>
          OutputFlightPoint(
            phase = state.detectedPhase,
            takeoff = detectedStages.takeoff,
            freefall = detectedStages.freefall,
            canopy = detectedStages.canopy,
            landing = detectedStages.landing,
            lastPoint = detectedStages.lastPoint,
            isValid = detectedStages.isValid,
            source = state.inputPoint.source,
          )
      }

  private def updateState[A](prevState: StreamState[A], point: InputFlightPoint[A], index: Long): StreamState[A] =
    // Push current value into smoothing window for filtered vertical speed
    val smoothingWindow = prevState.smoothingVerticalSpeedWindow.enqueue(point.verticalSpeed)

    // Compute all flight metrics including acceleration
    val snapshot = Calculations.computeFlightMetricsSnapshot(point, smoothingWindow, prevState.smoothedVerticalSpeed)

    // Update landing stability window
    val landingWindow = prevState.landingStabilityWindow.enqueue(snapshot.smoothedVerticalSpeed)

    // Push VerticalSpeedSample in backtracking window
    val verticalSpeedSample = VerticalSpeedSample(index, snapshot.smoothedVerticalSpeed, point.altitude)
    val backtrackWindow     = prevState.backtrackVerticalSpeedWindow.enqueue(verticalSpeedSample)

    // Detect flight phase based on current conditions
    val detectedPhase = detectFlightPhase(prevState, snapshot)

    // Check if we missed takeoff
    val assumedTakeoffMissed = TakeoffDetection.detectMissedTakeoff(prevState, point, index)

    StreamState(
      inputPoint = point,
      dataPointIndex = index,
      time = point.time,
      height = point.altitude,
      verticalSpeed = point.verticalSpeed,
      smoothedVerticalSpeed = snapshot.smoothedVerticalSpeed,
      verticalAccel = snapshot.verticalAcceleration,
      horizontalSpeed = snapshot.horizontalSpeed,
      totalSpeed = snapshot.totalSpeed,
      smoothingVerticalSpeedWindow = smoothingWindow,
      landingStabilityWindow = landingWindow,
      backtrackVerticalSpeedWindow = backtrackWindow,
      detectedPhase = detectedPhase,
      takeoffMissing = assumedTakeoffMissed,
    )

  private def detectFlightPhase[A](state: StreamState[A], snapshot: FlightMetricsSnapshot): FlightPhase =
    state.detectedPhase match {
      case FlightPhase.BeforeTakeoff =>
        if FreefallDetection.isFreefallCondition(snapshot) then
          FlightPhase.Freefall
        else if TakeoffDetection.isTakeoffCondition(snapshot) then
          FlightPhase.Takeoff
        else
          FlightPhase.BeforeTakeoff

      case FlightPhase.Takeoff =>
        if FreefallDetection.isFreefallCondition(snapshot) then
          FlightPhase.Freefall
        else
          FlightPhase.Takeoff

      case FlightPhase.Freefall =>
        if CanopyDetection.isCanopyCondition(snapshot) then
          FlightPhase.Canopy
        else
          FlightPhase.Freefall

      case FlightPhase.Canopy =>
        if LandingDetection.isLandingCondition(state, snapshot) then
          FlightPhase.Landing
        else
          FlightPhase.Canopy

      case FlightPhase.Landing =>
        FlightPhase.Landing
    }

  private def updateFlightStagesPoints[A](
    detectedStages: FlightStagesPoints,
    streamState: StreamState[A],
  ): FlightStagesPoints =
    val currentPoint = FlightStagePoint(streamState.dataPointIndex, streamState.height)

    val updatedPoints =
      for
        updatedTakeoff  <- TakeoffDetection.tryDetectTakeoff()
        updatedFreefall <- FreefallDetection.tryDetectFreefall(updatedTakeoff)
        updatedCanopy   <- CanopyDetection.tryDetectCanopy(updatedTakeoff, updatedFreefall)
        updatedLanding  <- LandingDetection.tryDetectLanding(updatedTakeoff, updatedCanopy)
      yield FlightStagesPoints(
        takeoff = updatedTakeoff,
        freefall = updatedFreefall,
        canopy = updatedCanopy,
        landing = updatedLanding,
        lastPoint = streamState.dataPointIndex,
        isValid = updatedFreefall.isDefined,
      )

    updatedPoints.run(DetectionConfig.default, streamState, detectedStages, currentPoint)

}
