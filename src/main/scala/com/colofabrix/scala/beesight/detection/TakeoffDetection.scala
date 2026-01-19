package com.colofabrix.scala.beesight.detection

import cats.data.Reader
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.config.DetectionConfig.default.*

private[detection] object TakeoffDetection {

  def detectMissedTakeoff[A](prevState: StreamState[A], point: InputFlightPoint[A], index: Long): Boolean =
    if index == 0 then
      point.altitude > TakeoffMaxAltitude && point.verticalSpeed < 0
    else
      prevState.takeoffMissing

  def isTakeoffCondition(snapshot: FlightMetricsSnapshot): Boolean =
    snapshot.horizontalSpeed > TakeoffSpeedThreshold &&
    snapshot.smoothedVerticalSpeed < TakeoffClimbRate

  def tryDetectTakeoff(): TryDetect[Option[FlightStagePoint]] =
    Reader { (config, streamState, detectedStages, currentPoint) =>
      if detectedStages.takeoff.isDefined then
        detectedStages.takeoff
      else if streamState.detectedPhase != FlightPhase.Takeoff then
        None
      else if streamState.takeoffMissing then
        None
      else if streamState.height >= config.TakeoffMaxAltitude then
        None
      else
        Some(currentPoint)
    }

}
