package com.colofabrix.scala.beesight.detection

import cats.data.Reader
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.config.DetectionConfig

private[detection] object FreefallDetection {

  def tryDetectFreefall(takeoff: Option[FlightStagePoint]): TryDetect[Option[FlightStagePoint]] =
    Reader { (config, result, state, currentPoint) =>
      if result.freefall.isDefined then
        result.freefall
      else if state.detectedPhase != FlightPhase.Freefall then
        None
      else
        val altitudeOk =
          takeoff.map(_.altitude) match {
            case Some(tAlt) => state.height > tAlt + config.FreefallMinAltitudeAbove
            case None       => state.height > config.FreefallMinAltitudeAbsolute
          }

        val afterTakeoff = takeoff isAfter state.dataPointIndex

        if altitudeOk && afterTakeoff then
          Some(Calculations.findInflectionPoint(state.vertSpeedHistory, currentPoint, isRising = true))
        else
          None
    }

}
