package com.colofabrix.scala.beesight.metrics.computations

import com.colofabrix.scala.beesight.math.*
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.readers.*
import java.time.Duration
import com.colofabrix.scala.beesight.metrics.computations.PerPointMetrics

/**
 * Flight metrics computed over a clamped window of points, with x = seconds relative to the first point of the
 * window.
 */
private[metrics] object WindowedMetrics {

  /** Component-wise least-squares slopes of the speed over the window (m/s²). */
  def acceleration[A: PointReader](window: Vector[A]): GeoVector =
    val times = relativeSeconds(window)

    val norths = window.toArrayBy(_.speed.north)
    val easts  = window.toArrayBy(_.speed.east)
    val downs  = window.toArrayBy(_.speed.vertical)

    GeoVector(
      north = times slopeAgainst norths,
      east = times slopeAgainst easts,
      vertical = times slopeAgainst downs,
    )

  /** Least-squares slope of the course over the window, unwrapped to a continuous signal (deg/s). */
  def courseRate[A: PointReader](window: Vector[A]): Double =
    val times  = relativeSeconds(window)
    val angles = unwrap(window.toArrayBy(point => rawHeading(point.speed)))

    times slopeAgainst angles

  /** Least-squares slope of the dive angle over the window (deg/s). */
  def diveRate[A: PointReader](window: Vector[A]): Double =
    val times  = relativeSeconds(window)
    val angles = window.toArrayBy(point => PerPointMetrics.diveAngle(point.speed))

    times slopeAgainst angles

  /** Least-squares slope of the total speed over the window (m/s²). */
  def totalSpeedRate[A: PointReader](window: Vector[A]): Double =
    val times  = relativeSeconds(window)
    val speeds = window.toArrayBy(_.speed.magnitude)

    times slopeAgainst speeds

  /** Instantaneous signed turn rate at the window center (deg/s); 0 for the first point or non-positive dt. */
  def turnRate[A: PointReader](centerIndex: Int, window: Vector[A]): Double =
    if centerIndex == 0 then
      0.0
    else
      val previous = window(centerIndex - 1)
      val current  = window(centerIndex)
      val dt       = Duration.between(previous.time, current.time).toMillis / 1000.0

      if dt <= 0.0 then
        0.0
      else
        val delta = PerPointMetrics.course(current.speed) - PerPointMetrics.course(previous.speed)
        val turn  = Heading.wrapSigned(delta)
        turn / dt

  /** Times of the window points in seconds, relative to the first point of the window. */
  private def relativeSeconds[A: PointReader](window: Vector[A]): Array[Double] =
    window.toArrayBy { point =>
      val start = window.head.time
      Duration.between(start, point.time).toMillis / 1000.0
    }

  private def rawHeading(speed: GeoVector): Double =
    Math.atan2(speed.east, speed.north) / Math.PI * 180.0

  private def unwrap(angles: Array[Double]): Array[Double] =
    val out = new Array[Double](angles.length)

    if angles.length > 0 then
      out(0) = angles(0)
      for i <- 1 until angles.length do
        val delta = angles(i) - angles(i - 1)

        val mapped =
          if Math.abs(Math.abs(delta) - 180.0) < 1e-9 then
            Math.signum(delta) * 180.0
          else if delta > 180.0 then
            delta - 360.0
          else if delta < -180.0 then
            delta + 360.0
          else
            delta

        out(i) = out(i - 1) + mapped

    out

}
