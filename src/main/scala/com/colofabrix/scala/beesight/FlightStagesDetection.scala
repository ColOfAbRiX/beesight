package com.colofabrix.scala.beesight

import cats.effect.IO
import com.colofabrix.scala.beesight.FlightStagesDetection.*
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.stats.*
import com.colofabrix.scala.beesight.stats.CusumDetector.DetectorState as CusumState
import java.time.*
import scala.collection.immutable.Queue

/**
 * Detects and analyses different stages of a flight based on time-series data
 */
object FlightStagesDetection {

  // Detection thresholds
  private val TakeoffSpeedThreshold         = 25.0  // m/s - horizontal speed above this indicates takeoff
  private val TakeoffClimbRate              = -1.0  // m/s - velocityDown below this indicates climbing (negative = up)
  private val TakeoffMaxAltitude            = 600.0 // m - takeoff cannot happen above this altitude
  private val FreefallVelocityDownThreshold = 25.0  // m/s - velocityDown above this indicates freefall
  private val FreefallAccelThreshold        = 3.0   // m/s per sample - rapid velocityDown increase indicates exit
  private val FreefallAccelMinVelocity      = 10.0  // m/s - minimum velocityDown for accel-based detection
  private val FreefallMinAltitudeAbove      = 600.0 // m - freefall must be at least this high above takeoff
  private val FreefallMinAltitudeAbsolute   = 600.0 // m - freefall min altitude when takeoff missed
  private val CanopyVelocityDownMax         = 12.0  // m/s - velocityDown below this after freefall indicates canopy
  private val LandingSpeedMax               = 3.0   // m/s - total speed below this indicates landing
  private val LandingAltitudeTolerance      = 500.0 // m - landing must be within ±this of takeoff altitude
  private val MedianFilterWindow            = 5     // points - window size for median filter
  private val BacknumberWindow              = 10    // points - how far back to look for true exit point

  private val freefallCusum = CusumDetector.withMedian(25, 0.5, 15.0)
  private val canopyCusum   = CusumDetector.withMedian(25, 0.5, 15.0)

  /**
   * Processes a stream of flight data points to identify flight stages
   */
  def detect(data: fs2.Stream[IO, FlysightPoint]): IO[FlightStagesPoints] =
    data
      .through(detectPipe)
      .compile
      .lastOrError

  private val detectPipe: fs2.Pipe[IO, FlysightPoint, FlightStagesPoints] =
    data =>
      data
        .zipWithIndex
        .scan(StreamState()) { case (state, (point, i)) => processPoint(state, point, i) }
        .fold(FlightStagesPoints.empty) { case (result, state) => updateResults(result, state) }

  private def processPoint(state: StreamState, point: FlysightPoint, index: Long): StreamState =
    val velocityDownWindow =
      SignalProcessor.updateWindow(state.velocityDownWindow, point.velD, MedianFilterWindow)

    val metrics =
      SignalProcessor.computeMetrics(
        altitude = point.hMSL,
        velocityNorth = point.velN,
        velocityEast = point.velE,
        velocityDown = point.velD,
        velocityDownWindow = velocityDownWindow,
      )

    val freefallCusumState = freefallCusum.checkNextValue(state.freefallCusum, metrics.filteredVelocityDown)
    val canopyCusumState   = canopyCusum.checkNextValue(state.canopyCusum, metrics.filteredVelocityDown)

    val updatedHistory =
      if state.velocityDownHistory.size >= BacknumberWindow then
        state.velocityDownHistory.tail :+ VelocityDownSample(index, metrics.filteredVelocityDown, point.hMSL)
      else
        state.velocityDownHistory :+ VelocityDownSample(index, metrics.filteredVelocityDown, point.hMSL)

    val accelerationDown = metrics.filteredVelocityDown - state.filteredVelocityDown
    val detectedPhase    = detectPhase(state, metrics, accelerationDown)
    val wasInFreefall    = state.wasInFreefall || detectedPhase == DetectedPhase.Freefall

    val assumedTakeoffMissed =
      if index == 0 then point.hMSL > TakeoffMaxAltitude && point.velD < 0
      else state.assumedTakeoffMissed

    StreamState(
      dataPointIndex = index,
      time = point.time.toInstant,
      height = point.hMSL,
      velocityDown = point.velD,
      filteredVelocityDown = metrics.filteredVelocityDown,
      accelerationDown = accelerationDown,
      horizontalSpeed = metrics.horizontalSpeed,
      totalSpeed = metrics.totalSpeed,
      velocityDownWindow = velocityDownWindow,
      velocityDownHistory = updatedHistory,
      freefallCusum = freefallCusumState,
      canopyCusum = canopyCusumState,
      detectedPhase = detectedPhase,
      wasInFreefall = wasInFreefall,
      assumedTakeoffMissed = assumedTakeoffMissed,
    )

  private def detectPhase(state: StreamState, metrics: SignalProcessor.FlightMetrics, accDown: Double): DetectedPhase =
    val isFreefallByThreshold = metrics.filteredVelocityDown > FreefallVelocityDownThreshold
    val isFreefallByAccel     = accDown > FreefallAccelThreshold && metrics.filteredVelocityDown > FreefallAccelMinVelocity

    if isFreefallByThreshold || isFreefallByAccel then
      DetectedPhase.Freefall
    else if state.wasInFreefall
        && metrics.filteredVelocityDown > 0
        && metrics.filteredVelocityDown < CanopyVelocityDownMax
    then
      DetectedPhase.Canopy
    else if metrics.totalSpeed < LandingSpeedMax
        && state.detectedPhase == DetectedPhase.Canopy
    then
      DetectedPhase.Landing
    else if !state.wasInFreefall
        && metrics.horizontalSpeed > TakeoffSpeedThreshold
        && metrics.filteredVelocityDown < TakeoffClimbRate
    then
      DetectedPhase.Takeoff
    else
      DetectedPhase.Unknown

  private def updateResults(result: FlightStagesPoints, state: StreamState): FlightStagesPoints =
    val currentPoint = DataPoint(state.dataPointIndex, Some(state.height))

    val updatedTakeoff  = tryDetectTakeoff(result, state, currentPoint)
    val updatedFreefall = tryDetectFreefall(result, state, currentPoint, updatedTakeoff)
    val updatedCanopy   = tryDetectCanopy(result, state, currentPoint, updatedTakeoff, updatedFreefall)
    val updatedLanding  = tryDetectLanding(result, state, currentPoint, updatedTakeoff, updatedCanopy)

    FlightStagesPoints(
      takeoff = updatedTakeoff,
      freefall = updatedFreefall,
      canopy = updatedCanopy,
      landing = updatedLanding,
      lastPoint = state.dataPointIndex,
      isValid = updatedFreefall.isDefined,
    )

  // ─── Detection Methods ─────────────────────────────────────────────────────────

  private def tryDetectTakeoff(
    result: FlightStagesPoints,
    state: StreamState,
    currentPoint: DataPoint,
  ): Option[DataPoint] =
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

  private def tryDetectFreefall(
    result: FlightStagesPoints,
    state: StreamState,
    currentPoint: DataPoint,
    takeoff: Option[DataPoint],
  ): Option[DataPoint] =
    if result.freefall.isDefined then
      result.freefall
    else if state.detectedPhase != DetectedPhase.Freefall then
      None
    else
      // Altitude constraints
      val altitudeOk = takeoff.flatMap(_.altitude) match {
        case Some(tAlt) => state.height > tAlt + FreefallMinAltitudeAbove
        case None       => state.height > FreefallMinAltitudeAbsolute
      }

      // Time ordering: freefall must be after takeoff
      val afterTakeoff = isAfter(state.dataPointIndex, takeoff)

      if altitudeOk && afterTakeoff then
        Some(findInflectionPoint(state.velocityDownHistory, currentPoint, isRising = true))
      else
        None

  private def tryDetectCanopy(
    result: FlightStagesPoints,
    state: StreamState,
    currentPoint: DataPoint,
    takeoff: Option[DataPoint],
    freefall: Option[DataPoint],
  ): Option[DataPoint] =
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
          .flatMap(_.altitude)
          .forall(tAlt => state.height > tAlt)

      val belowFreefall =
        freefall
          .flatMap(_.altitude)
          .forall(fAlt => state.height < fAlt)

      // Time ordering: canopy must be after freefall
      val afterFreefall = isAfter(state.dataPointIndex, freefall)

      if aboveTakeoff && belowFreefall && afterFreefall then
        Some(findInflectionPoint(state.velocityDownHistory, currentPoint, isRising = false))
      else
        None

  private def tryDetectLanding(
    result: FlightStagesPoints,
    state: StreamState,
    currentPoint: DataPoint,
    takeoff: Option[DataPoint],
    canopy: Option[DataPoint],
  ): Option[DataPoint] =
    if result.landing.isDefined then
      result.landing
    else if state.detectedPhase != DetectedPhase.Landing then
      None
    else if canopy.isEmpty then
      None // Requires canopy to have been detected
    else
      // Altitude constraint: within ±LandingAltitudeTolerance of takeoff
      val altitudeOk = takeoff.flatMap(_.altitude) match {
        case Some(tAlt) => Math.abs(state.height - tAlt) < LandingAltitudeTolerance
        case None       => true
      }

      // Time ordering: landing must be after canopy
      val afterCanopy = isAfter(state.dataPointIndex, canopy)

      if altitudeOk && afterCanopy then
        Some(currentPoint)
      else
        None

  // ─── Helpers ───────────────────────────────────────────────────────────────────

  private def isAfter(currentIndex: Long, point: Option[DataPoint]): Boolean =
    point.map(_.lineIndex).forall(currentIndex > _)

  private def findInflectionPoint(
    history: Vector[VelocityDownSample],
    detectedPoint: DataPoint,
    isRising: Boolean,
  ): DataPoint =
    if history.isEmpty then
      detectedPoint
    else
      val candidate =
        history
          .sliding(2)
          .collect {
            case Vector(prev, curr) if isRising && curr.velocityDown > prev.velocityDown + 0.5  => prev
            case Vector(prev, curr) if !isRising && curr.velocityDown < prev.velocityDown - 0.5 => prev
          }
          .toList
          .headOption
          .getOrElse(if isRising then history.head else history.maxBy(_.velocityDown))

      DataPoint(candidate.index, Some(candidate.altitude))

  // ─── Types ─────────────────────────────────────────────────────────────────────

  private enum DetectedPhase {
    case Unknown, Takeoff, Freefall, Canopy, Landing
  }

  final private case class VelocityDownSample(index: Long, velocityDown: Double, altitude: Double)

  final private case class StreamState(
    dataPointIndex: Long = 0,
    time: Instant = Instant.EPOCH,
    height: Double = 0.0,
    velocityDown: Double = 0.0,
    filteredVelocityDown: Double = 0.0,
    accelerationDown: Double = 0.0,
    horizontalSpeed: Double = 0.0,
    totalSpeed: Double = 0.0,
    velocityDownWindow: Queue[Double] = Queue.empty,
    velocityDownHistory: Vector[VelocityDownSample] = Vector.empty,
    freefallCusum: CusumState = CusumState.Empty,
    canopyCusum: CusumState = CusumState.Empty,
    detectedPhase: DetectedPhase = DetectedPhase.Unknown,
    wasInFreefall: Boolean = false,
    assumedTakeoffMissed: Boolean = false,
  )

}
