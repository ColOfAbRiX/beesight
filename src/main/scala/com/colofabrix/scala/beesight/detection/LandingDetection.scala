package com.colofabrix.scala.beesight.detection

import cats.data.Reader
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.config.DetectionConfig.default.*

private[detection] object LandingDetection {

  def isLandingCondition[A](streamState: StreamState[A], snapshot: FlightMetricsSnapshot): Boolean =
    lazy val windowStable = isWindowStable(streamState.landingStabilityWindow.toVector, LandingStabilityWindowSize)
    snapshot.totalSpeed < LandingSpeedMax && windowStable

  private def isWindowStable(history: Vector[Double], windowSize: Int): Boolean =
    if history.size < windowSize then
      false
    else
      val mean     = history.sum / history.size
      val variance = history.map(v => Math.pow(v - mean, 2)).sum / history.size
      val stdDev   = Math.sqrt(variance)

      stdDev < LandingStabilityThreshold &&
      Math.abs(mean) < LandingMeanVerticalSpeedMax

  def tryDetectLanding(
    takeoff: Option[FlightStagePoint],
    canopy: Option[FlightStagePoint],
  ): TryDetect[Option[FlightStagePoint]] =
    Reader { (config, streamState, detectedStages, currentPoint) =>
      if detectedStages.landing.isDefined then
        detectedStages.landing
      else if streamState.detectedPhase != FlightPhase.Landing then
        None
      else if canopy.isEmpty then
        None // Requires canopy to have been detected
      else
        // Altitude constraint: within ±LandingAltitudeTolerance of takeoff
        val altitudeOk =
          takeoff.map(_.altitude) match {
            case Some(tAlt) => Math.abs(streamState.height - tAlt) < config.LandingAltitudeTolerance
            case None       => true
          }

        val belowCanopy =
          canopy
            .map(_.altitude)
            .forall(cAlt => streamState.height < cAlt)

        val afterCanopy = canopy isAfter streamState.dataPointIndex

        if altitudeOk && belowCanopy && afterCanopy then
          Some(Calculations.findInflectionPoint(
            streamState.backtrackVerticalSpeedWindow.toVector,
            currentPoint,
            isRising = false,
          ))
        else
          None
    }

}
