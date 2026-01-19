package com.colofabrix.scala.beesight.detection

import cats.data.Reader
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.config.DetectionConfig.default.*

private[detection] object CanopyDetection {

  def isCanopyCondition(snapshot: FlightMetricsSnapshot): Boolean =
    snapshot.smoothedVerticalSpeed > 0 &&
    snapshot.smoothedVerticalSpeed < CanopyVerticalSpeedMax

  def tryDetectCanopy(
    takeoff: Option[FlightStagePoint],
    freefall: Option[FlightStagePoint],
  ): TryDetect[Option[FlightStagePoint]] =
    Reader { (_, streamState, detectedStages, currentPoint) =>
      if detectedStages.canopy.isDefined then
        detectedStages.canopy
      else if streamState.detectedPhase != FlightPhase.Canopy then
        None
      else if freefall.isEmpty then
        None // Requires freefall to have been detected
      else
        val aboveTakeoff =
          takeoff
            .map(_.altitude)
            .forall(tAlt => streamState.height > tAlt)

        val belowFreefall =
          freefall
            .map(_.altitude)
            .forall(fAlt => streamState.height < fAlt)

        val afterFreefall = freefall isAfter streamState.dataPointIndex

        if aboveTakeoff && belowFreefall && afterFreefall then
          Some(Calculations.findInflectionPoint(
            streamState.backtrackVerticalSpeedWindow.toVector,
            currentPoint,
            isRising = false,
          ))
        else
          None
    }

}
