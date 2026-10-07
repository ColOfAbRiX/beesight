package com.colofabrix.scala.beesight.detection.swoop.model

import com.colofabrix.scala.beesight.detection.math.Heading
import com.colofabrix.scala.beesight.detection.model.IndexedPoint
import java.time.Instant

/**
 * Indexed access to the sequence of flight data points, exposing the per-sample quantities used by the swoop
 * detection stages: speeds, altitude, heading, and time.
 */
final private[swoop] class PointSeries(val points: Vector[IndexedPoint]) {

  def apply(index: Int): IndexedPoint =
    points(index)

  def length: Int =
    points.length

  def verticalSpeed(index: Int): Double =
    points(index).speed.vertical

  def horizontalSpeed(index: Int): Double =
    points(index).speed.horizontal

  def totalSpeed(index: Int): Double =
    points(index).speed.magnitude

  def northSpeed(index: Int): Double =
    points(index).speed.north

  def eastSpeed(index: Int): Double =
    points(index).speed.east

  def altitude(index: Int): Double =
    points(index).altitude

  def heading(index: Int): Double =
    Heading.headingDegrees(points(index).speed)

  def time(index: Int): Instant =
    points(index).time

  def timeMillis(index: Int): Double =
    points(index).time.toEpochMilli.toDouble

}

private[swoop] object PointSeries {

  def apply(points: Vector[IndexedPoint]): PointSeries =
    new PointSeries(points)

}
