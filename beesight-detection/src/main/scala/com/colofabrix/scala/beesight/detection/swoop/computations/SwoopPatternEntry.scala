package com.colofabrix.scala.beesight.detection.swoop.computations

import com.colofabrix.scala.beesight.detection.config.{ Altitude, SwoopConfig }
import com.colofabrix.scala.beesight.detection.swoop.model.PointSeries

/**
 * The pattern entry: the first crossing below the pattern-entry altitude above the detected ground.
 */
private[swoop] object SwoopPatternEntry {

  def find(series: PointSeries, ground: Altitude, config: SwoopConfig): Option[Int] =
    val agl = (i: Int) => series.altitude(i) - ground.value

    val patternEntryAltitude = config.patternEntry.entryAltitude.value

    (0 until series.length - 1)
      .find { i =>
        val aboveOrAt = agl(i) >= patternEntryAltitude
        val below     = agl(i + 1) < patternEntryAltitude
        aboveOrAt && below
      }
      .map(_ + 1)

}
