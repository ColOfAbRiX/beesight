package com.colofabrix.scala.beesight.detection

import cats.data.Reader
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

  private type TryDetect[B] =
    Reader[(FlightStagesPoints, StreamState[?], FlightStagePoint), B]

  private val freefallCusum = CusumDetector.withMedian(FreefallWindow, 0.5, 15.0)
  private val canopyCusum   = CusumDetector.withMedian(CanopyWindow, 0.5, 15.0)

  /**
   * Streaming detection - emits an OutputFlightPoint for each input
   */
  def streamDetectA[F[_], A](using A: FlightInfo[A]): fs2.Pipe[F, A, OutputFlightPoint[A]] = data =>
    data
      .map(A.toInputFlightPoint)
      .zipWithIndex
      .scan((Option.empty[StreamState[A]], FlightStagesPoints.empty)) {
        case ((stateOpt, result), (point, i)) =>
          val newState  = processInputPoint(stateOpt, point, i)
          val newResult = updateResults(result, newState)
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

  private def processInputPoint[A](
    stateOpt: Option[StreamState[A]],
    point: InputFlightPoint[A],
    index: Long,
  ): StreamState[A] =
    val prevState = stateOpt.getOrElse(StreamState.empty(point))

    val vertSpeedWindow = updateWindow(prevState.vertSpeedWindow, point.verticalSpeed, MedianFilterWindow)
    val metrics         = Calculations.computeMetrics(point, vertSpeedWindow)
    val verticalAccel   = metrics.filteredVertSpeed - prevState.filteredVertSpeed
    val detectedPhase   = detectPhase(prevState, metrics, verticalAccel)
    val wasInFreefall   = prevState.wasInFreefall || detectedPhase == FlightPhase.Freefall

    val freefallCusumState = freefallCusum.checkNextValue(prevState.freefallCusum, metrics.filteredVertSpeed)
    val canopyCusumState   = canopyCusum.checkNextValue(prevState.canopyCusum, metrics.filteredVertSpeed)

    val updatedHistory =
      if prevState.vertSpeedHistory.size >= BacknumberWindow then
        prevState.vertSpeedHistory.tail :+ VerticalSpeedSample(index, metrics.filteredVertSpeed, point.altitude)
      else
        prevState.vertSpeedHistory :+ VerticalSpeedSample(index, metrics.filteredVertSpeed, point.altitude)

    val assumedTakeoffMissed =
      if index == 0 then
        point.altitude > TakeoffMaxAltitude && point.verticalSpeed < 0
      else
        prevState.assumedTakeoffMissed

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

  private def detectPhase[A](state: StreamState[A], metrics: FlightMetrics, accDown: Double): FlightPhase =
    if state.detectedPhase == FlightPhase.Landing then
      // Terminal state - stay landed
      FlightPhase.Landing
    else if state.detectedPhase == FlightPhase.Canopy then
      // In canopy - can only transition to landing
      val windowStable = isWindowStable(state.vertSpeedHistory)
      if metrics.totalSpeed < LandingSpeedMax && windowStable then
        FlightPhase.Landing
      else
        FlightPhase.Canopy
    else if state.wasInFreefall then
      // After freefall detected - can only transition to canopy
      if metrics.filteredVertSpeed > 0 && metrics.filteredVertSpeed < CanopyVerticalSpeedMax then
        FlightPhase.Canopy
      else
        FlightPhase.Freefall
    else if state.detectedPhase == FlightPhase.Takeoff then
      // In takeoff - can only transition to freefall
      val isFreefallByThreshold = metrics.filteredVertSpeed > FreefallVerticalSpeedThreshold
      val isFreefallByAccel     =
        accDown > FreefallAccelThreshold && metrics.filteredVertSpeed > FreefallAccelMinVelocity

      if isFreefallByThreshold || isFreefallByAccel then
        FlightPhase.Freefall
      else
        FlightPhase.Takeoff
    else
      // Before takeoff - check for freefall entry or takeoff
      val isFreefallByThreshold = metrics.filteredVertSpeed > FreefallVerticalSpeedThreshold
      val isFreefallByAccel     =
        accDown > FreefallAccelThreshold && metrics.filteredVertSpeed > FreefallAccelMinVelocity

      if isFreefallByThreshold || isFreefallByAccel then
        FlightPhase.Freefall
      else if metrics.horizontalSpeed > TakeoffSpeedThreshold && metrics.filteredVertSpeed < TakeoffClimbRate then
        FlightPhase.Takeoff
      else
        FlightPhase.Unknown

  private def updateResults[A](result: FlightStagesPoints, state: StreamState[A]): FlightStagesPoints =
    val currentPoint = FlightStagePoint(state.dataPointIndex, state.height)

    val updatedPoints =
      for
        updatedTakeoff  <- tryDetectTakeoff()
        updatedFreefall <- tryDetectFreefall(updatedTakeoff)
        updatedCanopy   <- tryDetectCanopy(updatedTakeoff, updatedFreefall)
        updatedLanding  <- tryDetectLanding(updatedTakeoff, updatedCanopy)
      yield FlightStagesPoints(
        takeoff = updatedTakeoff,
        freefall = updatedFreefall,
        canopy = updatedCanopy,
        landing = updatedLanding,
        lastPoint = state.dataPointIndex,
        isValid = updatedFreefall.isDefined,
      )

    updatedPoints.run(result, state, currentPoint)

  private def tryDetectTakeoff(): TryDetect[Option[FlightStagePoint]] =
    Reader { (result, state, currentPoint) =>
      if result.takeoff.isDefined then
        result.takeoff
      else if state.detectedPhase != FlightPhase.Takeoff then
        None
      else if state.assumedTakeoffMissed then
        None // Data started after takeoff
      else if state.height >= TakeoffMaxAltitude then
        None // Must be below TakeoffMaxAltitude
      else
        Some(currentPoint)
    }

  private def tryDetectFreefall(takeoff: Option[FlightStagePoint]): TryDetect[Option[FlightStagePoint]] =
    Reader { (result, state, currentPoint) =>
      if result.freefall.isDefined then
        result.freefall
      else if state.detectedPhase != FlightPhase.Freefall then
        None
      else
        val altitudeOk =
          takeoff.map(_.altitude) match {
            case Some(tAlt) => state.height > tAlt + FreefallMinAltitudeAbove
            case None       => state.height > FreefallMinAltitudeAbsolute
          }

        val afterTakeoff = takeoff isAfter state.dataPointIndex

        if altitudeOk && afterTakeoff then
          Some(Calculations.findInflectionPoint(state.vertSpeedHistory, currentPoint, isRising = true))
        else
          None
    }

  private def tryDetectCanopy(
    takeoff: Option[FlightStagePoint],
    freefall: Option[FlightStagePoint],
  ): TryDetect[Option[FlightStagePoint]] =
    Reader { (result, state, currentPoint) =>
      if result.canopy.isDefined then
        result.canopy
      else if state.detectedPhase != FlightPhase.Canopy then
        None
      else if freefall.isEmpty then
        None // Requires freefall to have been detected
      else
        val aboveTakeoff =
          takeoff
            .map(_.altitude)
            .forall(tAlt => state.height > tAlt)

        val belowFreefall =
          freefall
            .map(_.altitude)
            .forall(fAlt => state.height < fAlt)

        val afterFreefall = freefall isAfter state.dataPointIndex

        if aboveTakeoff && belowFreefall && afterFreefall then
          Some(Calculations.findInflectionPoint(state.vertSpeedHistory, currentPoint, isRising = false))
        else
          None
    }

  private def tryDetectLanding(
    takeoff: Option[FlightStagePoint],
    canopy: Option[FlightStagePoint],
  ): TryDetect[Option[FlightStagePoint]] =
    Reader { (result, state, currentPoint) =>
      if result.landing.isDefined then
        result.landing
      else if state.detectedPhase != FlightPhase.Landing then
        None
      else if canopy.isEmpty then
        None // Requires canopy to have been detected
      else
        // Altitude constraint: within ±LandingAltitudeTolerance of takeoff
        val altitudeOk =
          takeoff.map(_.altitude) match {
            case Some(tAlt) => Math.abs(state.height - tAlt) < LandingAltitudeTolerance
            case None       => true
          }

        val belowCanopy =
          canopy
            .map(_.altitude)
            .forall(cAlt => state.height < cAlt)

        val afterCanopy = canopy isAfter state.dataPointIndex

        if altitudeOk && belowCanopy && afterCanopy then
          Some(Calculations.findInflectionPoint(state.vertSpeedHistory, currentPoint, isRising = false))
        else
          None
    }

  extension (self: Option[FlightStagePoint]) {
    private infix def isAfter(currentIndex: Long): Boolean =
      self.map(_.lineIndex).forall(currentIndex > _)
  }

  private def isWindowStable(history: Vector[VerticalSpeedSample]): Boolean =
    if history.size < BacknumberWindow then
      false
    else
      val speeds   = history.map(_.verticalSpeed)
      val mean     = speeds.sum / speeds.size
      val variance = speeds.map(v => Math.pow(v - mean, 2)).sum / speeds.size
      val stdDev   = Math.sqrt(variance)
      stdDev < LandingStabilityThreshold && Math.abs(mean) < LandingMeanVerticalSpeedMax

  private def updateWindow(window: Queue[Double], value: Double, maxSize: Int): Queue[Double] =
    val updated = window.enqueue(value)
    if updated.size > maxSize then
      updated.dequeue._2
    else
      updated

}
