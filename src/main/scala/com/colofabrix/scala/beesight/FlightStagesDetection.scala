package com.colofabrix.scala.beesight

import cats.effect.IO
import com.colofabrix.scala.beesight.FlightStagesDetection.*
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.stats.*
import com.colofabrix.scala.beesight.stats.CusumDetector.DetectorState as CusumState
import java.time.*
import scala.collection.immutable.Queue
import cats.data.Reader

/**
 * Detects and analyses different stages of a flight based on time-series data
 */
object FlightStagesDetection {

  // Detection thresholds
  private val TakeoffSpeedThreshold          = 25.0  // m/s - horizontal speed above this indicates takeoff
  private val TakeoffClimbRate               = -1.0  // m/s - verticalSpeed below this indicates climbing (negative = up)
  private val TakeoffMaxAltitude             = 600.0 // m - takeoff cannot happen above this altitude
  private val FreefallVerticalSpeedThreshold = 25.0  // m/s - verticalSpeed above this indicates freefall
  private val FreefallAccelThreshold         = 3.0   // m/s per sample - rapid verticalSpeed increase indicates exit
  private val FreefallAccelMinVelocity       = 10.0  // m/s - minimum verticalSpeed for accel-based detection
  private val FreefallMinAltitudeAbove       = 600.0 // m - freefall must be at least this high above takeoff
  private val FreefallMinAltitudeAbsolute    = 600.0 // m - freefall min altitude when takeoff missed
  private val CanopyVerticalSpeedMax         = 12.0  // m/s - verticalSpeed below this after freefall indicates canopy
  private val LandingSpeedMax                = 3.0   // m/s - total speed below this indicates landing
  private val LandingAltitudeTolerance       = 500.0 // m - landing must be within ±this of takeoff altitude
  private val MedianFilterWindow             = 5     // points - window size for median filter
  private val BacknumberWindow               = 10    // points - how far back to look for true exit point

  private val freefallCusum = CusumDetector.withMedian(25, 0.5, 15.0)
  private val canopyCusum   = CusumDetector.withMedian(25, 0.5, 15.0)

  /**
   * Processes a stream of flight data points to identify flight stages
   */
  def detect(data: fs2.Stream[IO, FlysightPoint]): IO[FlightStagesPoints] =
    data
      .through {
        _.zipWithIndex
          .scan(StreamState()) {
            case (state, (point, i)) => processPoint(state, point, i)
          }
          .fold(FlightStagesPoints.empty) {
            case (result, state) => updateResults(result, state)
          }
      }
      .compile
      .lastOrError

  // TODO: Return each input FlysightPoint enriched with Stages info
  def detectDebug: fs2.Pipe[IO, FlysightPoint, (FlysightPoint, FlightStagesPoints)] =
    _.zipWithIndex
      .scan(StreamState()) {
        case (state, (point, i)) => processPoint(state, point, i)
      }
      .fold(FlightStagesPoints.empty) {
        case (result, state) =>
          updateResults(result, state)
      }
      .as(???)

  private def processPoint(state: StreamState, point: FlysightPoint, index: Long): StreamState =
    val verticalSpeedWindow =
      SignalProcessor.updateWindow(state.verticalSpeedWindow, point.velD, MedianFilterWindow)

    val metrics = SignalProcessor.computeMetrics(point.hMSL, point.velN, point.velE, point.velD, verticalSpeedWindow)

    val accelerationDown = metrics.filteredVerticalSpeed - state.filteredVerticalSpeed
    val detectedPhase    = detectPhase(state, metrics, accelerationDown)
    val wasInFreefall    = state.wasInFreefall || detectedPhase == DetectedPhase.Freefall

    val freefallCusumState = freefallCusum.checkNextValue(state.freefallCusum, metrics.filteredVerticalSpeed)
    val canopyCusumState   = canopyCusum.checkNextValue(state.canopyCusum, metrics.filteredVerticalSpeed)

    val updatedHistory =
      if state.verticalSpeedHistory.size >= BacknumberWindow then
        state.verticalSpeedHistory.tail :+ VerticalSpeedSample(index, metrics.filteredVerticalSpeed, point.hMSL)
      else
        state.verticalSpeedHistory :+ VerticalSpeedSample(index, metrics.filteredVerticalSpeed, point.hMSL)

    val assumedTakeoffMissed =
      if index == 0 then point.hMSL > TakeoffMaxAltitude && point.velD < 0
      else state.assumedTakeoffMissed

    StreamState(
      dataPointIndex = index,
      time = point.time.toInstant,
      height = point.hMSL,
      verticalSpeed = point.velD,
      filteredVerticalSpeed = metrics.filteredVerticalSpeed,
      accelerationDown = accelerationDown,
      horizontalSpeed = metrics.horizontalSpeed,
      totalSpeed = metrics.totalSpeed,
      verticalSpeedWindow = verticalSpeedWindow,
      verticalSpeedHistory = updatedHistory,
      freefallCusum = freefallCusumState,
      canopyCusum = canopyCusumState,
      detectedPhase = detectedPhase,
      wasInFreefall = wasInFreefall,
      assumedTakeoffMissed = assumedTakeoffMissed,
    )

  private def detectPhase(state: StreamState, metrics: SignalProcessor.FlightMetrics, accDown: Double): DetectedPhase =
    val isFreefallByThreshold = metrics.filteredVerticalSpeed > FreefallVerticalSpeedThreshold
    val isFreefallByAccel     = accDown > FreefallAccelThreshold && metrics.filteredVerticalSpeed > FreefallAccelMinVelocity

    if isFreefallByThreshold || isFreefallByAccel then
      DetectedPhase.Freefall
    else if state.wasInFreefall
        && metrics.filteredVerticalSpeed > 0
        && metrics.filteredVerticalSpeed < CanopyVerticalSpeedMax
    then
      DetectedPhase.Canopy
    else if metrics.totalSpeed < LandingSpeedMax
        && state.detectedPhase == DetectedPhase.Canopy
    then
      DetectedPhase.Landing
    else if !state.wasInFreefall
        && metrics.horizontalSpeed > TakeoffSpeedThreshold
        && metrics.filteredVerticalSpeed < TakeoffClimbRate
    then
      DetectedPhase.Takeoff
    else
      DetectedPhase.Unknown

  private def updateResults(result: FlightStagesPoints, state: StreamState): FlightStagesPoints =
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

  // ─── Detection Methods ─────────────────────────────────────────────────────────

  private type TryDetect[A] = Reader[(FlightStagesPoints, StreamState, FlightStagePoint), A]

  private def tryDetectTakeoff(): TryDetect[Option[FlightStagePoint]] =
    Reader { (result, state, currentPoint) =>
      if result.takeoff.isDefined then
        result.takeoff
      else if state.detectedPhase != DetectedPhase.Takeoff then
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
      else if state.detectedPhase != DetectedPhase.Freefall then
        None
      else
        // Altitude constraints
        val altitudeOk = takeoff.map(_.altitude) match {
          case Some(tAlt) => state.height > tAlt + FreefallMinAltitudeAbove
          case None       => state.height > FreefallMinAltitudeAbsolute
        }

        // Time ordering: freefall must be after takeoff
        val afterTakeoff = takeoff isAfter state.dataPointIndex

        if altitudeOk && afterTakeoff then
          Some(findInflectionPoint(state.verticalSpeedHistory, currentPoint, isRising = true))
        else
          None
    }

  private def tryDetectCanopy(takeoff: Option[FlightStagePoint], freefall: Option[FlightStagePoint]): TryDetect[Option[FlightStagePoint]] =
    Reader { (result, state, currentPoint) =>
      if result.canopy.isDefined then
        result.canopy
      else if state.detectedPhase != DetectedPhase.Canopy then
        None
      else if freefall.isEmpty then
        None // Requires freefall to have been detected
      else
        // Altitude constraints
        val aboveTakeoff =
          takeoff
            .map(_.altitude)
            .forall(tAlt => state.height > tAlt)

        val belowFreefall =
          freefall
            .map(_.altitude)
            .forall(fAlt => state.height < fAlt)

        // Time ordering: canopy must be after freefall
        val afterFreefall = freefall isAfter state.dataPointIndex

        if aboveTakeoff && belowFreefall && afterFreefall then
          Some(findInflectionPoint(state.verticalSpeedHistory, currentPoint, isRising = false))
        else
          None
    }

  private def tryDetectLanding(takeoff: Option[FlightStagePoint], canopy: Option[FlightStagePoint]): TryDetect[Option[FlightStagePoint]] =
    Reader { (result, state, currentPoint) =>
      if result.landing.isDefined then
        result.landing
      else if state.detectedPhase != DetectedPhase.Landing then
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

        // Time ordering: landing must be after canopy
        val afterCanopy = canopy isAfter state.dataPointIndex

        if altitudeOk && afterCanopy then
          Some(currentPoint)
        else
          None
    }

  // ─── Helpers ───────────────────────────────────────────────────────────────────

  extension (self: Option[FlightStagePoint]) {
    private infix def isAfter(currentIndex: Long): Boolean =
      self.map(_.lineIndex).forall(currentIndex > _)
  }

  private def findInflectionPoint(
    history: Vector[VerticalSpeedSample],
    detectedPoint: FlightStagePoint,
    isRising: Boolean,
  ): FlightStagePoint =
    if history.isEmpty then
      detectedPoint
    else
      val candidate =
        history
          .sliding(2)
          .collect {
            case Vector(prev, curr) if isRising && curr.verticalSpeed > prev.verticalSpeed + 0.5  => prev
            case Vector(prev, curr) if !isRising && curr.verticalSpeed < prev.verticalSpeed - 0.5 => prev
          }
          .toList
          .headOption
          .getOrElse(if isRising then history.head else history.maxBy(_.verticalSpeed))

      FlightStagePoint(candidate.index, candidate.altitude)

  // ─── Types ─────────────────────────────────────────────────────────────────────

  private enum DetectedPhase {
    case Unknown, Takeoff, Freefall, Canopy, Landing
  }

  final private case class VerticalSpeedSample(
    index: Long,
    verticalSpeed: Double,
    altitude: Double,
  )

  final private case class StreamState(
    dataPointIndex: Long = 0,
    time: Instant = Instant.EPOCH,
    height: Double = 0.0,
    verticalSpeed: Double = 0.0,
    filteredVerticalSpeed: Double = 0.0,
    accelerationDown: Double = 0.0,
    horizontalSpeed: Double = 0.0,
    totalSpeed: Double = 0.0,
    verticalSpeedWindow: Queue[Double] = Queue.empty,
    verticalSpeedHistory: Vector[VerticalSpeedSample] = Vector.empty,
    freefallCusum: CusumState = CusumState.Empty,
    canopyCusum: CusumState = CusumState.Empty,
    detectedPhase: DetectedPhase = DetectedPhase.Unknown,
    wasInFreefall: Boolean = false,
    assumedTakeoffMissed: Boolean = false,
  )

}
