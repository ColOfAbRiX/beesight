package com.colofabrix.scala.beesight.detection

import cats.data.Reader
import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.config.DetectionConfig.default.*
import com.colofabrix.scala.beesight.detection.FlightStagesDetection.*
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.stats.*
import com.colofabrix.scala.beesight.stats.CusumDetector.CusumState
import java.time.*
import scala.collection.immutable.Queue

/**
 * Detects and analyses different stages of a flight based on time-series data
 */
object FlightStagesDetection {

  private val freefallCusum = CusumDetector.withMedian(DetectionConfig.default.FreefallWindow, 0.5, 15.0)
  private val canopyCusum   = CusumDetector.withMedian(DetectionConfig.default.CanopyWindow, 0.5, 15.0)

  /**
   * Streaming detection - emits an OutputFlightPoint for each input
   */
  def streamDetectA[F[_], A](using A: FlightInfo[A]): fs2.Pipe[F, A, OutputFlightPoint[A]] = data =>
    data
      .map(A.toInputFlightPoint)
      .zipWithIndex
      .scan((Option.empty[StreamState[A]], FlightStagesPoints.empty)) {
        case ((None, result), (point, i)) =>
          // First data row, initialize the state
          val initialState =
            StreamState(
              inputPoint = point,
              vertSpeedWindow = FixedSizeQueue(MedianFilterWindow),
            )
          val newState  = updateState(initialState, point, i)
          val newResult = updateDetectedPoints(result, newState)
          (Some(newState), newResult)
        case ((Some(state), result), (point, i)) =>
          // Process each data row
          val newState  = updateState(state, point, i)
          val newResult = updateDetectedPoints(result, newState)
          (Some(newState), newResult)
      }
      .collect {
        case (Some(state), result) =>
          OutputFlightPoint(
            phase = state.detectedPhase,
            takeoff = result.takeoff,
            freefall = result.freefall,
            canopy = result.canopy,
            landing = result.landing,
            lastPoint = result.lastPoint,
            isValid = result.isValid,
            source = state.inputPoint.source,
          )
      }

  private def updateState[A](prevState: StreamState[A], point: InputFlightPoint[A], index: Long): StreamState[A] =
    // Push current value in the window
    val vertSpeedWindow = prevState.vertSpeedWindow.enqueue(point.verticalSpeed)

    // Calculate speeds and other metrics
    val metrics = Calculations.computeFlightMetrics(point, vertSpeedWindow)

    // Smoothed vertical acceleration
    val verticalAccel = metrics.filteredVertSpeed - prevState.filteredVertSpeed

    // Detect flight phase based on current conditions
    val detectedPhase = guessFlightPhase(prevState, metrics, verticalAccel)

    // Peak detection with CUSUM for frefall and canopy points
    val freefallCusumState = freefallCusum.checkNextValue(prevState.freefallCusum, metrics.filteredVertSpeed)
    val canopyCusumState   = canopyCusum.checkNextValue(prevState.canopyCusum, metrics.filteredVertSpeed)

    // Push VerticalSpeedSample in the speed history
    val updatedHistory =
      if prevState.vertSpeedHistory.size >= BacknumberWindow then
        prevState.vertSpeedHistory.tail :+ VerticalSpeedSample(index, metrics.filteredVertSpeed, point.altitude)
      else
        prevState.vertSpeedHistory :+ VerticalSpeedSample(index, metrics.filteredVertSpeed, point.altitude)

    // Check if we missed takeoff
    val assumedTakeoffMissed = detectMissedTakeoff(prevState, point, index)

    // Check if freefall happeend
    val wasInFreefall = prevState.wasInFreefall || detectedPhase == FlightPhase.Freefall

    StreamState(
      inputPoint = point,
      dataPointIndex = index,
      time = point.time,
      height = point.altitude,
      verticalSpeed = point.verticalSpeed,
      filteredVertSpeed = metrics.filteredVertSpeed,
      verticalAccel = verticalAccel,
      horizontalSpeed = metrics.horizontalSpeed,
      totalSpeed = metrics.totalSpeed,
      vertSpeedWindow = vertSpeedWindow,
      vertSpeedHistory = updatedHistory,
      freefallCusum = freefallCusumState,
      canopyCusum = canopyCusumState,
      detectedPhase = detectedPhase,
      wasInFreefall = wasInFreefall,
      assumedTakeoffMissed = assumedTakeoffMissed,
    )

  private def detectMissedTakeoff[A](prevState: StreamState[A], point: InputFlightPoint[A], index: Long): Boolean =
    if index == 0 then
      point.altitude > TakeoffMaxAltitude && point.verticalSpeed < 0
    else
      prevState.assumedTakeoffMissed

  private def guessFlightPhase[A](state: StreamState[A], metrics: FlightMetrics, accDown: Double): FlightPhase =
    state.detectedPhase match {
      case FlightPhase.Takeoff =>
        val isFreefallByThreshold = metrics.filteredVertSpeed > FreefallVerticalSpeedThreshold
        val isFreefallByAccel     =
          accDown > FreefallAccelThreshold && metrics.filteredVertSpeed > FreefallAccelMinVelocity

        if isFreefallByThreshold || isFreefallByAccel then
          FlightPhase.Freefall
        else
          FlightPhase.Takeoff

      case FlightPhase.Canopy =>
        val windowStable = isWindowStable(state.vertSpeedHistory)
        if metrics.totalSpeed < LandingSpeedMax && windowStable then
          FlightPhase.Landing
        else
          FlightPhase.Canopy

      case FlightPhase.Landing =>
        FlightPhase.Landing

      case _ if state.wasInFreefall =>
        // BeforeTakeoff or Freefall

        if metrics.filteredVertSpeed > 0 && metrics.filteredVertSpeed < CanopyVerticalSpeedMax then
          FlightPhase.Canopy
        else
          FlightPhase.Freefall

      case _ =>
        // BeforeTakeoff or Freefall

        val isFreefallByThreshold = metrics.filteredVertSpeed > FreefallVerticalSpeedThreshold
        val isFreefallByAccel     =
          accDown > FreefallAccelThreshold && metrics.filteredVertSpeed > FreefallAccelMinVelocity

        if isFreefallByThreshold || isFreefallByAccel then
          FlightPhase.Freefall
        else if metrics.horizontalSpeed > TakeoffSpeedThreshold && metrics.filteredVertSpeed < TakeoffClimbRate then
          FlightPhase.Takeoff
        else
          FlightPhase.BeforeTakeoff
    }

  private def updateDetectedPoints[A](result: FlightStagesPoints, state: StreamState[A]): FlightStagesPoints =
    val currentPoint = FlightStagePoint(state.dataPointIndex, state.height)

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
        lastPoint = state.dataPointIndex,
        isValid = updatedFreefall.isDefined,
      )

    updatedPoints.run(DetectionConfig.default, result, state, currentPoint)

  private def isWindowStable(history: Vector[VerticalSpeedSample]): Boolean =
    if history.size < BacknumberWindow then
      false
    else
      val speeds   = history.map(_.verticalSpeed)
      val mean     = speeds.sum / speeds.size
      val variance = speeds.map(v => Math.pow(v - mean, 2)).sum / speeds.size
      val stdDev   = Math.sqrt(variance)
      stdDev < LandingStabilityThreshold && Math.abs(mean) < LandingMeanVerticalSpeedMax

}
