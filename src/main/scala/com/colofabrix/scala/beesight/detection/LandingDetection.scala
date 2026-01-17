package com.colofabrix.scala.beesight.detection

import cats.data.Reader
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.config.DetectionConfig

object LandingDetection {

  def tryDetectLanding(
    takeoff: Option[FlightStagePoint],
    canopy: Option[FlightStagePoint],
  ): TryDetect[Option[FlightStagePoint]] =
    Reader { (config, result, state, currentPoint) =>
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
            case Some(tAlt) => Math.abs(state.height - tAlt) < config.LandingAltitudeTolerance
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

}
