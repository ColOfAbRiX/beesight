package com.colofabrix.scala.beesight

import cats.data.NonEmptyList
import cats.effect.IO
import cats.implicits.*
import com.colofabrix.scala.beesight.FlightStagesDetection.*
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.stats.*
import com.colofabrix.scala.stats.CusumDetector.DetectorState as CusumState
import com.colofabrix.scala.stats.SignalProcessor
import fs2.*
import fs2.data.csv.*
import java.time.*
import scala.collection.immutable.Queue

/**
 * Detects and analyses different stages of a flight based on time-series data
 */
object FlightStagesDetection {

  // Detection thresholds
  private val TakeoffSpeedThreshold           = 25.0   // m/s - horizontal speed above this indicates takeoff
  private val TakeoffClimbRate                = -1.0   // m/s - velocityDown below this indicates climbing (negative = up)
  private val FreefallVelocityDownThreshold   = 20.0   // m/s - velocityDown above this indicates freefall
  private val FreefallAccelThreshold          = 3.0    // m/s per sample - rapid velocityDown increase indicates exit
  private val CanopyVelocityDownMax           = 12.0   // m/s - velocityDown below this after freefall indicates canopy
  private val LandingSpeedMax                 = 3.0    // m/s - total speed below this indicates landing
  private val MedianFilterWindow              = 5      // points - window size for median filter
  private val BacknumberWindow                = 25     // points - how far back to look for true exit point
  private val TakeoffMaxAltitude              = 600.0  // m - takeoff cannot happen above this altitude
  private val FreefallMinAltitudeAboveTakeoff = 600.0  // m - freefall must be at least this high above takeoff
  private val FreefallMinAltitudeAbsolute     = 1000.0 // m - freefall must be at least this high (when takeoff missed)
  private val LandingAltitudeTolerance        = 500.0  // m - landing must be within ±this of takeoff altitude

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

  private val detectPipe: Pipe[IO, FlysightPoint, FlightStagesPoints] =
    data =>
      data
        .zipWithIndex
        .scan(StreamState()) {
          case (state, (point, i)) =>
            processPoint(state, point, i)
        }
        .fold(FlightStagesPoints.empty) {
          case (result, state) =>
            updateResults(result, state)
        }

  private def processPoint(state: StreamState, point: FlysightPoint, index: Long): StreamState =
    val time = point.time.toInstant

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

    // Update velocityDown history for backnumbering (keep last BacknumberWindow points)
    val updatedVelocityDownHistory =
      if state.velocityDownHistory.size >= BacknumberWindow then
        state.velocityDownHistory.tail :+ VelocityDownSample(index, metrics.filteredVelocityDown, point.hMSL)
      else
        state.velocityDownHistory :+ VelocityDownSample(index, metrics.filteredVelocityDown, point.hMSL)

    val accelerationDown = metrics.filteredVelocityDown - state.filteredVelocityDown

    // Detect if we started recording after takeoff (data starts high and climbing)
    val assumedTakeoffMissed =
      if index == 0 then
        point.hMSL > TakeoffMaxAltitude && point.velD < 0 // Above 600m and climbing
      else
        state.assumedTakeoffMissed

    val detectedPhase = detectPhase(state, metrics, accelerationDown, freefallCusumState, canopyCusumState)
    val wasInFreefall = state.wasInFreefall || detectedPhase == DetectedPhase.Freefall

    StreamState(
      dataPointIndex = index,
      time = time,
      height = point.hMSL,
      velocityDown = point.velD,
      filteredVelocityDown = metrics.filteredVelocityDown,
      accelerationDown = accelerationDown,
      horizontalSpeed = metrics.horizontalSpeed,
      totalSpeed = metrics.totalSpeed,
      velocityDownWindow = velocityDownWindow,
      velocityDownHistory = updatedVelocityDownHistory,
      freefallCusum = freefallCusumState,
      canopyCusum = canopyCusumState,
      detectedPhase = detectedPhase,
      wasInFreefall = wasInFreefall,
      assumedTakeoffMissed = assumedTakeoffMissed,
    )

  private def detectPhase(
    state: StreamState,
    metrics: SignalProcessor.FlightMetrics,
    accelerationDown: Double,
    @annotation.nowarn("msg=unused") freefallCusum: CusumState,
    @annotation.nowarn("msg=unused") canopyCusum: CusumState,
  ): DetectedPhase =
    // Freefall detection: threshold OR rapid acceleration
    val isFreefallByThreshold = metrics.filteredVelocityDown > FreefallVelocityDownThreshold
    val isFreefallByAccel     = accelerationDown > FreefallAccelThreshold && metrics.filteredVelocityDown > 10.0

    if isFreefallByThreshold || isFreefallByAccel then
      DetectedPhase.Freefall
    // Canopy: velocityDown dropped to low value after being in freefall
    else if state.wasInFreefall && metrics.filteredVelocityDown > 0 && metrics.filteredVelocityDown < CanopyVelocityDownMax
    then
      DetectedPhase.Canopy
    // Landing: very low total speed (near zero)
    else if metrics.totalSpeed < LandingSpeedMax && state.detectedPhase == DetectedPhase.Canopy then
      DetectedPhase.Landing
    // Takeoff: high horizontal speed + climbing (negative velocityDown) + not yet in freefall
    else if !state.wasInFreefall && metrics.horizontalSpeed > TakeoffSpeedThreshold && metrics.filteredVelocityDown < TakeoffClimbRate
    then
      DetectedPhase.Takeoff
    // No specific phase detected
    else
      DetectedPhase.Unknown

  private def updateResults(result: FlightStagesPoints, state: StreamState): FlightStagesPoints =
    val currentPoint = DataPoint(state.dataPointIndex, Some(state.height))

    // Takeoff constraint: only detect if altitude < 600m and not already assumed missed
    val updatedTakeoff =
      if result.takeoff.isEmpty
          && state.detectedPhase == DetectedPhase.Takeoff
          && !state.assumedTakeoffMissed
          && state.height < TakeoffMaxAltitude
      then
        Some(currentPoint)
      else
        result.takeoff

    // Get takeoff altitude for constraints (if known)
    val takeoffAltitude = updatedTakeoff.flatMap(_.altitude)

    // Freefall constraint: must be above takeoff + 600m (or > 1000m if takeoff missed)
    val freefallAltitudeOk =
      takeoffAltitude match {
        case Some(tAlt) => state.height > tAlt + FreefallMinAltitudeAboveTakeoff
        case None       => state.height > FreefallMinAltitudeAbsolute
      }

    val updatedFreefall =
      if result.freefall.isEmpty && state.detectedPhase == DetectedPhase.Freefall && freefallAltitudeOk then
        // Backnumber: find where acceleration actually started
        val backnumberedPoint = findExitStart(state.velocityDownHistory, currentPoint)
        Some(backnumberedPoint)
      else
        result.freefall

    // Canopy constraint: requires freefall to have been detected (already enforced by wasInFreefall)
    val updatedCanopy =
      if result.canopy.isEmpty && state.detectedPhase == DetectedPhase.Canopy && result.freefall.isDefined then
        Some(currentPoint)
      else
        result.canopy

    // Landing constraints:
    // 1. Requires canopy to have been detected
    // 2. Altitude must be within ±500m of takeoff (if takeoff known)
    val landingAltitudeOk =
      takeoffAltitude match {
        case Some(tAlt) => Math.abs(state.height - tAlt) < LandingAltitudeTolerance
        case None       => true // No constraint if takeoff altitude unknown
      }

    val updatedLanding =
      if result.landing.isEmpty
          && state.detectedPhase == DetectedPhase.Landing
          && updatedCanopy.isDefined
          && landingAltitudeOk
      then
        Some(currentPoint)
      else
        result.landing

    // File is valid if freefall was detected
    val isValid = updatedFreefall.isDefined

    FlightStagesPoints(
      takeoff = updatedTakeoff,
      freefall = updatedFreefall,
      canopy = updatedCanopy,
      landing = updatedLanding,
      lastPoint = state.dataPointIndex,
      isValid = isValid,
    )

  /**
   * Backnumber to find the true exit point by scanning history for where acceleration began
   */
  private def findExitStart(history: Vector[VelocityDownSample], detectedPoint: DataPoint): DataPoint =
    if history.isEmpty then
      detectedPoint
    else
      // Find the point where velocityDown was lowest before it started rising
      // This is the true exit point
      val minVelocityDownSample =
        history
          .sliding(2)
          .collect {
            case Vector(prev, curr) if curr.velocityDown > prev.velocityDown + 0.5 => prev
          }
          .toList
          .headOption
          .getOrElse(history.head)

      DataPoint(minVelocityDownSample.index, Some(minVelocityDownSample.altitude))

  /**
   * Detected flight phases
   */
  enum DetectedPhase {
    case Unknown, Takeoff, Freefall, Canopy, Landing
  }

  /**
   * Sample of velocityDown at a specific index for backnumbering
   */
  final private case class VelocityDownSample(
    index: Long,
    velocityDown: Double,
    altitude: Double,
  )

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
