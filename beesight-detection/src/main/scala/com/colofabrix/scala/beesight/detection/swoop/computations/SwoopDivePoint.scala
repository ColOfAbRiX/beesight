package com.colofabrix.scala.beesight.detection.swoop.computations

import com.colofabrix.scala.beesight.detection.config.{ Altitude, SwoopConfig }
import com.colofabrix.scala.beesight.detection.swoop.model.PointSeries

/**
 * The dive point: the first point achieving the maximum descent rate below the altitude cap. Points at or below
 * zero descent never qualify, so profiles without a dive yield no swoop.
 */
private[swoop] object SwoopDivePoint {

  def find(series: PointSeries, patternEntry: Int, ground: Altitude, config: SwoopConfig): Option[Int] =
    val agl = (i: Int) => series.altitude(i) - ground.value

    (patternEntry until series.length)
      .view
      .filter { i =>
        agl(i) < config.altitudes.diveAltitudeCap.value && series.verticalSpeed(i) > 0
      }
      .maxByOption(series.verticalSpeed)

}
