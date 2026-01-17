package com.colofabrix.scala.beesight.detection

import cats.data.Reader
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.config.DetectionConfig

object CanopyDetection {

  def tryDetectCanopy(
    takeoff: Option[FlightStagePoint],
    freefall: Option[FlightStagePoint],
  ): TryDetect[Option[FlightStagePoint]] =
    Reader { (_, result, state, currentPoint) =>
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

}
