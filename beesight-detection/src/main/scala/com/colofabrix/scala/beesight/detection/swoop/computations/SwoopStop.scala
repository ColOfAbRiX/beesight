package com.colofabrix.scala.beesight.detection.swoop.computations

import com.colofabrix.scala.beesight.detection.config.SwoopConfig
import com.colofabrix.scala.beesight.detection.math.Heading
import com.colofabrix.scala.beesight.detection.swoop.model.PointSeries

/**
 * The stop: from the dive point forward, the first point below the slow descent and slow horizontal thresholds
 * fixes the swoop heading; from there forward, the first point below the end horizontal speed or deviating more
 * than the heading deviation from the swoop heading is the stop.
 */
private[swoop] object SwoopStop {

  def find(series: PointSeries, diveIdx: Int, config: SwoopConfig): Option[Int] = {
    val headingIdx = (diveIdx until series.length)
      .find { i =>
        val slowDescent    = series.verticalSpeed(i) < config.speeds.stopDescentThreshold.value
        val slowHorizontal = series.horizontalSpeed(i) < config.speeds.stopHorizontalThreshold.value
        slowDescent && slowHorizontal
      }

    headingIdx.flatMap { slowIdx =>
      lazy val swoopHeading = series.heading(slowIdx)

      (slowIdx until series.length)
        .find { i =>
          val nearlyStopped = series.horizontalSpeed(i) < config.speeds.stopEndHorizontal.value

          val deviation    = Heading.angularDistance(series.heading(i), swoopHeading)
          val maxDeviation = config.angles.stopHeadingDeviation.value
          val turnedAway   = deviation > maxDeviation

          nearlyStopped || turnedAway
        }
    }
  }

}
