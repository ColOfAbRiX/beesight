package com.colofabrix.scala.beesight.detection

import com.colofabrix.scala.beesight.collections.FixedSizeQueue
import com.colofabrix.scala.beesight.detection.model.VerticalSpeedSample
import com.colofabrix.scala.beesight.model.FlightPoint

object InflectionFinder {

  def findInflectionPoint(window: FixedSizeQueue[VerticalSpeedSample], isRising: Boolean): Option[FlightPoint] = {
    val samples = window.toVector

    if (samples.size < 2) {
      samples.headOption.map(s => FlightPoint(s.index, s.altitude))
    } else {
      val pairs = samples.sliding(2).toVector

      val inflectionIndex =
        pairs.indexWhere {
          case Vector(prev, curr) =>
            if (isRising) curr.speed > prev.speed
            else curr.speed < prev.speed
          case _ =>
            false
        }

      if (inflectionIndex >= 0) {
        val sample = samples(inflectionIndex)
        Some(FlightPoint(sample.index, sample.altitude))
      } else {
        samples.headOption.map(s => FlightPoint(s.index, s.altitude))
      }
    }
  }

}
