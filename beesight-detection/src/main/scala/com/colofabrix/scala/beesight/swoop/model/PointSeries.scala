package com.colofabrix.scala.beesight.swoop.model

import com.colofabrix.scala.beesight.math.Heading
import com.colofabrix.scala.beesight.model.IndexedPoint
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
    points(index).point.speed.vertical

  def horizontalSpeed(index: Int): Double =
    points(index).point.speed.horizontal

  def totalSpeed(index: Int): Double =
    points(index).point.speed.magnitude

  def northSpeed(index: Int): Double =
    points(index).point.speed.north

  def eastSpeed(index: Int): Double =
    points(index).point.speed.east

  def altitude(index: Int): Double =
    points(index).point.altitude

  def heading(index: Int): Double =
    Heading.headingDegrees(points(index).point.speed)

  def time(index: Int): Instant =
    points(index).point.time

  def timeMillis(index: Int): Double =
    points(index).point.time.toEpochMilli.toDouble

}

private[swoop] object PointSeries {

  def apply(points: Vector[IndexedPoint]): PointSeries =
    new PointSeries(points)

}
