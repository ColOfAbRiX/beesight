package com.colofabrix.scala.beesight

import cats.data.NonEmptyList
import cats.effect.IO
import cats.implicits.*
import com.colofabrix.scala.beesight.FlightStagesDetection.*
import com.colofabrix.scala.beesight.StreamUtils.*
import com.colofabrix.scala.beesight.config.*
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
final class FlightStagesDetection(@annotation.nowarn("msg=unused") config: Config) {

  // Detection thresholds - easy to tune
  private val TakeoffSpeedThreshold = 25.0 // m/s - horizontal speed above this indicates takeoff
  private val TakeoffClimbRate      = -1.0 // m/s - velD below this indicates climbing (negative = up)
  private val FreefallVelDThreshold = 30.0 // m/s - velD above this indicates freefall
  private val CanopyVelDMax         = 12.0 // m/s - velD below this after freefall indicates canopy
  private val LandingSpeedMax       = 3.0  // m/s - total speed below this indicates landing
  private val MedianFilterWindow    = 5    // points - window size for median filter (~1 second at 5Hz)

  // CUSUM detectors for different phases
  // Parameters: windowSize, slack, threshold
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
        .through(writeDebug)
        .fold(FlightStagesPoints.empty) {
          case (result, state) =>
            updateResults(result, state)
        }

  private def processPoint(state: StreamState, point: FlysightPoint, index: Long): StreamState =
    val time = point.time.toInstant

    // Update the velD window for median filtering
    val velDWindow = SignalProcessor.updateWindow(state.velDWindow, point.velD, MedianFilterWindow)

    // Compute flight metrics with filtering
    val metrics =
      SignalProcessor.computeMetrics(
        altitude = point.hMSL,
        velN = point.velN,
        velE = point.velE,
        velD = point.velD,
        velDWindow = velDWindow,
      )

    // Apply CUSUM on filtered velD for freefall detection (looking for high positive velD)
    val freefallCusumState = freefallCusum.checkNextValue(state.freefallCusum, metrics.filteredVelD)

    // Apply CUSUM on filtered velD for canopy detection (looking for deceleration)
    val canopyCusumState = canopyCusum.checkNextValue(state.canopyCusum, metrics.filteredVelD)

    // Detect flight phases
    val detectedPhase = detectPhase(state, metrics, freefallCusumState, canopyCusumState)

    StreamState(
      dataPointIndex = index,
      time = time,
      height = point.hMSL,
      velD = point.velD,
      filteredVelD = metrics.filteredVelD,
      horizontalSpeed = metrics.horizontalSpeed,
      totalSpeed = metrics.totalSpeed,
      velDWindow = velDWindow,
      freefallCusum = freefallCusumState,
      canopyCusum = canopyCusumState,
      detectedPhase = detectedPhase,
      wasInFreefall = state.wasInFreefall || detectedPhase == DetectedPhase.Freefall,
    )

  private def detectPhase(
    state: StreamState,
    metrics: SignalProcessor.FlightMetrics,
    @annotation.nowarn("msg=unused") freefallCusum: CusumState,
    @annotation.nowarn("msg=unused") canopyCusum: CusumState,
  ): DetectedPhase =
    // Freefall: velD consistently above threshold (positive = falling)
    if metrics.filteredVelD > FreefallVelDThreshold then
      DetectedPhase.Freefall
    // Canopy: velD dropped to low value after being in freefall
    else if state.wasInFreefall && metrics.filteredVelD > 0 && metrics.filteredVelD < CanopyVelDMax then
      DetectedPhase.Canopy
    // Landing: very low total speed (near zero)
    else if metrics.totalSpeed < LandingSpeedMax && state.detectedPhase == DetectedPhase.Canopy then
      DetectedPhase.Landing
    // Takeoff: high horizontal speed + climbing (negative velD) + not yet in freefall
    else if !state.wasInFreefall && metrics.horizontalSpeed > TakeoffSpeedThreshold && metrics.filteredVelD < TakeoffClimbRate then
      DetectedPhase.Takeoff
    // No specific phase detected
    else
      DetectedPhase.Unknown

  private def updateResults(result: FlightStagesPoints, state: StreamState): FlightStagesPoints =
    val currentPoint = DataPoint(state.dataPointIndex, Some(state.height))

    // Update takeoff detection - capture first takeoff point
    val updatedTakeoff =
      if result.takeoff.isEmpty && state.detectedPhase == DetectedPhase.Takeoff then
        Some(currentPoint)
      else
        result.takeoff

    // Update freefall detection - capture first freefall point
    val updatedFreefall =
      if result.freefall.isEmpty && state.detectedPhase == DetectedPhase.Freefall then
        Some(currentPoint)
      else
        result.freefall

    // Update canopy detection - capture first canopy point
    val updatedCanopy =
      if result.canopy.isEmpty && state.detectedPhase == DetectedPhase.Canopy then
        Some(currentPoint)
      else
        result.canopy

    // Update landing detection - capture first landing point
    val updatedLanding =
      if result.landing.isEmpty && state.detectedPhase == DetectedPhase.Landing then
        Some(currentPoint)
      else
        result.landing

    FlightStagesPoints(
      takeoff = updatedTakeoff,
      freefall = updatedFreefall,
      canopy = updatedCanopy,
      landing = updatedLanding,
      lastPoint = state.dataPointIndex,
    )

  private def writeDebug: Pipe[IO, StreamState, StreamState] =
    _.evalTap(_ => IO.unit) // Pass through - debug writing handled separately for now

}

object FlightStagesDetection {

  /**
   * Detected flight phases
   */
  enum DetectedPhase {
    case Unknown
    case Takeoff
    case Freefall
    case Canopy
    case Landing
  }

  final private case class StreamState(
    dataPointIndex: Long = 0,
    time: Instant = Instant.EPOCH,
    height: Double = 0.0,
    velD: Double = 0.0,
    filteredVelD: Double = 0.0,
    horizontalSpeed: Double = 0.0,
    totalSpeed: Double = 0.0,
    velDWindow: Queue[Double] = Queue.empty,
    freefallCusum: CusumState = CusumState.Empty,
    canopyCusum: CusumState = CusumState.Empty,
    detectedPhase: DetectedPhase = DetectedPhase.Unknown,
    wasInFreefall: Boolean = false,
  )

  private object StreamState {
    import com.colofabrix.scala.beesight.csv.Encoders.*

    given csvRowEncoder: CsvRowEncoder[StreamState, String] with {

      def apply(row: StreamState): CsvRow[String] =
        CsvRow.fromNelHeaders {
          NonEmptyList
            .of(
              ("dataPointIndex", row.dataPointIndex.toString),
              ("time", formatInstant(row.time, 3)),
              ("height", formatDouble(row.height, 3)),
              ("velD", formatDouble(row.velD, 2)),
              ("filteredVelD", formatDouble(row.filteredVelD, 2)),
              ("horizontalSpeed", formatDouble(row.horizontalSpeed, 2)),
              ("totalSpeed", formatDouble(row.totalSpeed, 2)),
              ("detectedPhase", row.detectedPhase.toString),
              ("wasInFreefall", row.wasInFreefall.toString),
            )
            .appendList(cusumSelector("freefall", row.freefallCusum))
            .appendList(cusumSelector("canopy", row.canopyCusum))
        }

    }

    /**
     * Encodes CusumState for CSV output
     */
    def cusumSelector(prefix: String, state: CusumState): List[(String, String)] =
      val aPrefix = if prefix.isEmpty then "" else prefix + "_"
      state match {
        case _: CusumState.Empty.type =>
          List(
            (aPrefix + "currentValue", ""),
            (aPrefix + "windowAverage", ""),
            (aPrefix + "windowStDev", ""),
            (aPrefix + "positiveSum", ""),
            (aPrefix + "negativeSum", ""),
            (aPrefix + "peakResult", ""),
          )
        case _: CusumState.Filling =>
          List(
            (aPrefix + "currentValue", ""),
            (aPrefix + "windowAverage", ""),
            (aPrefix + "windowStDev", ""),
            (aPrefix + "positiveSum", ""),
            (aPrefix + "negativeSum", ""),
            (aPrefix + "peakResult", ""),
          )
        case d: CusumState.Detection =>
          List(
            (aPrefix + "currentValue", formatDouble(d.currentValue, 3)),
            (aPrefix + "windowAverage", formatDouble(d.windowAverage, 3)),
            (aPrefix + "windowStDev", formatDouble(d.windowStDev, 3)),
            (aPrefix + "positiveSum", formatDouble(d.positiveSum, 3)),
            (aPrefix + "negativeSum", formatDouble(d.negativeSum, 3)),
            (aPrefix + "peakResult", d.peakResult.toString),
          )
      }

  }

}
