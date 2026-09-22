package com.colofabrix.scala.beesight.jumpevents

import cats.syntax.all.*
import com.colofabrix.scala.beesight.collections.*
import com.colofabrix.scala.beesight.collections.NEW.*
import com.colofabrix.scala.beesight.config.JumpEventsConfig
import com.colofabrix.scala.beesight.math.*
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.utils.*

/**
 * Detects and removes velocity spikes by interpolating outlier points.
 */
private[jumpevents] class SpeedClipper private (config: JumpEventsConfig) {

  private val windowCapacity =
    Math.max(2, config.preprocessWindowSize.value)

  /**
   * Removes GPS data spikes and smooths velocity readings via a small sliding window.
   */
  def clip[F[_]]: fs2.Pipe[F, IndexedPoint, IndexedPoint] =
    _.statefulMapEmits(initialState, drainWindow) { (currentRow, window) =>
      val clipped =
        window match {
          case filled: NonEmptyFilled[PointKinematics] => clipWindow(filled)
          case other                                   => other
        }

      clipped
        .push {
          Kinematics.compute(window.newest.point, currentRow)
        }
        .swap
        .bimap(identity, _.map(_.point))
    }

  private def initialState(firstPoint: IndexedPoint): (NEW[PointKinematics], Option[IndexedPoint]) =
    val windowData    = PointKinematics(firstPoint, GeoVector.zero)
    val initialWindow = NEW(windowCapacity, windowData)
    (initialWindow, None)

  private def drainWindow(window: NEW[PointKinematics]): Vector[IndexedPoint] =
    window
      .toVector
      .map(_.point)

  private def clipWindow(window: NonEmptyFilled[PointKinematics]): NonEmptyFilled[PointKinematics] =
    val oldest       = window.oldest
    val newest       = window.newest
    val focused      = window.focusAt(1)
    val secondOldest = focused.focus

    val interpolated = oldest.interpolate(newest)(secondOldest.point.time)

    // The interpolated point replaces the focused point, so it inherits its index
    val expectedPoint =
      interpolated.copy(point = interpolated.point.copy(idx = secondOldest.point.idx))

    val actualVelocity   = secondOldest.point.speed.vertical
    val expectedVelocity = expectedPoint.point.speed.vertical
    val deviation        = Math.abs(actualVelocity - expectedVelocity)

    if deviation <= config.accelerationClip then
      focused
    else
      focused.modifyFocus(_ => expectedPoint)

}

private[jumpevents] object SpeedClipper {

  def apply(config: JumpEventsConfig): SpeedClipper =
    new SpeedClipper(config)

}
