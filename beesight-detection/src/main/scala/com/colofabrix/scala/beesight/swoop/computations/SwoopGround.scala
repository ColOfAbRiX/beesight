package com.colofabrix.scala.beesight.swoop.computations

import cats.syntax.all.*
import com.colofabrix.scala.beesight.config.{ Altitude, SwoopConfig }
import com.colofabrix.scala.beesight.math.*
import com.colofabrix.scala.beesight.model.EventPoint
import com.colofabrix.scala.beesight.swoop.model.PointSeries
import java.time.Duration

/**
 * Ground altitude estimation for swoop reporting: all reported altitudes are above ground level (AGL).
 */
final private[swoop] class SwoopGround private (config: SwoopConfig) {

  /**
   * Ground altitude.
   *
   * Primary calculation: the median altitude over the window of points starting at the detected landing point,
   * located by its time within the series. Fallback (no landing event): the last slow point's forward window.
   */
  def detect(series: PointSeries, landing: Option[EventPoint]): Altitude =
    landing
      .flatMap { l =>
        val start = series.points.indexWhere(!_.time.isBefore(l.time))
        if start >= 0 then 
          Altitude(medianAltitude(series, start, config.ground.window)).some
        else 
          None
      }
      .orElse {
        (series.length - 1 to 0 by -1)
          .find { i =>
            series.totalSpeed(i) < config.ground.slowSpeed.value
          }
          .map { slowIdx =>
            Altitude(medianAltitude(series, slowIdx, config.ground.window))
          }
      }
      .getOrElse {
        Altitude(series.altitude(series.length - 1))
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

private[swoop] object SwoopGround {

  def apply(config: SwoopConfig): SwoopGround =
    new SwoopGround(config)

}
