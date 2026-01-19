package com.colofabrix.scala.beesight.detection

import cats.data.Reader
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.config.DetectionConfig.default.*

private[detection] object FreefallDetection {

  def isFreefallCondition(snapshot: FlightMetricsSnapshot): Boolean =
    val byThreshold = snapshot.smoothedVerticalSpeed > FreefallVerticalSpeedThreshold

    val byAccel =
      snapshot.verticalAcceleration > FreefallAccelThreshold &&
      snapshot.smoothedVerticalSpeed > FreefallAccelMinVelocity

    byThreshold || byAccel

  def tryDetectFreefall(takeoff: Option[FlightStagePoint]): TryDetect[Option[FlightStagePoint]] =
    Reader { (config, streamState, detectedStages, currentPoint) =>
      if detectedStages.freefall.isDefined then
        detectedStages.freefall
      else if streamState.detectedPhase != FlightPhase.Freefall then
        None
      else
        val altitudeValid =
          takeoff.map(_.altitude) match {
            case Some(tAlt) => streamState.height > tAlt + config.FreefallMinAltitudeAbove
            case None       => streamState.height > config.FreefallMinAltitudeAbsolute
          }

        val afterTakeoff = takeoff isAfter streamState.dataPointIndex

        if altitudeValid && afterTakeoff then
          Some(Calculations.findInflectionPoint(
            streamState.backtrackVerticalSpeedWindow.toVector,
            currentPoint,
            isRising = true,
          ))
        else
          None
    }

}
