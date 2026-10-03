package com.colofabrix.scala.beesight.swoop.computations

import com.colofabrix.scala.beesight.config.SwoopConfig
import com.colofabrix.scala.beesight.swoop.model.PointSeries
import scala.annotation.tailrec

/**
 * The rollout start: from the top of turn forward, the first point less than the rollout window above the stop
 * altitude starts the walk; walking backward while the descent rate strictly increases yields the
 * maximum-descent-rate point - the rollout start. The walk never moving keeps the starting point; reaching the top
 * of turn fails the detection.
 */
private[swoop] object SwoopRolloutStart {

  def find(series: PointSeries, topOfTurn: Int, stopIdx: Int, config: SwoopConfig): Option[Int] =
    val rolloutWindow = config.altitudes.rolloutWindow.value

    @tailrec
    def advance(i: Int): Int =
      if i <= stopIdx && (series.altitude(i) - series.altitude(stopIdx)) >= rolloutWindow then advance(i + 1)
      else i

    val startIdx = advance(topOfTurn)

    @tailrec
    def walk(curIdx: Int, negVzMax: Double): Option[Int] =
      val prev = curIdx - 1
      if prev < topOfTurn then
        None // could not find the rollout
      else if negVzMax < series.verticalSpeed(prev) then
        walk(prev, series.verticalSpeed(prev))
      else
        Some(curIdx)

    walk(startIdx, series.verticalSpeed(startIdx))

}
