package com.colofabrix.scala.beesight.detection

import cats.data.Reader
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.config.DetectionConfig

private[detection] object TakeoffDetection {

  def tryDetectTakeoff(): TryDetect[Option[FlightStagePoint]] =
    Reader { (config, result, state, currentPoint) =>
      if result.takeoff.isDefined then
        result.takeoff
      else if state.detectedPhase != FlightPhase.Takeoff then
        None
      else if state.assumedTakeoffMissed then
        None // Data started after takeoff
      else if state.height >= config.TakeoffMaxAltitude then
        None // Must be below TakeoffMaxAltitude
      else
        Some(currentPoint)
    }

}
