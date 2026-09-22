package com.colofabrix.scala.beesight.swoop

import com.colofabrix.scala.beesight.math.*
import com.colofabrix.scala.beesight.model.EventPoint
import com.colofabrix.scala.beesight.swoop.model.PointSeries
import java.time.Duration

/**
 * Ground altitude estimation for swoop reporting: all reported altitudes are above ground level (AGL).
 */
private[swoop] object SwoopGround {

  private val groundWindow =
    Duration.ofSeconds(10)

  /**
   * Ground altitude.
   *
   * Primary calculation: the median altitude over the window of points starting at the detected landing point.
   * Fallback (no landing event): the last slow point's forward window.
   */
  def detect(series: PointSeries, landing: Option[EventPoint]): Double =
    landing
      .collect {
        case l if 0 <= l.index && l.index < series.length =>
          medianAltitude(series, l.index.toInt, groundWindow)
      }
      .orElse {
        (series.length - 1 to 0 by -1)
          .find { i =>
            series.totalSpeed(i) < SwoopConstants.groundSlowSpeed
          }
          .map { slowIdx =>
            medianAltitude(series, slowIdx, groundWindow)
          }
      }
      .getOrElse {
        series.altitude(series.length - 1)
      }

  private def medianAltitude(series: PointSeries, fromIndex: Int, duration: Duration): Double =
    val startTime = series.time(fromIndex)

    series
      .points
      .drop(fromIndex)
      .takeWhile(!_.time.isAfter(startTime.plus(duration)))
      .toArrayBy(_.altitude)
      .median

}
