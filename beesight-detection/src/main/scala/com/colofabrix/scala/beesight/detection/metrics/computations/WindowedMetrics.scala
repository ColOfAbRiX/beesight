package com.colofabrix.scala.beesight.detection.metrics.computations

import com.colofabrix.scala.beesight.detection.math.*
import com.colofabrix.scala.beesight.detection.metrics.computations.PerPointMetrics
import com.colofabrix.scala.beesight.detection.metrics.computations.WindowedMetrics.SlopeMetrics
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.detection.readers.*
import java.time.Duration

/**
 * Flight metrics computed over a clamped window of points, with x = seconds relative to the first point of the
 * window.
 */
final private[beesight] class WindowedMetrics[A: PointReader] private (window: Vector[A]) {

  private lazy val slopeMetrics: SlopeMetrics =
    val times  = relativeSeconds
    val points = window

    val norths = points.toArrayBy(_.speed.north)
    val easts  = points.toArrayBy(_.speed.east)
    val downs  = points.toArrayBy(_.speed.vertical)

    val acceleration =
      GeoVector(
        north = times slopeAgainst norths,
        east = times slopeAgainst easts,
        vertical = times slopeAgainst downs,
      )

    val courseAngles = unwrap(points.toArrayBy(point => rawHeading(point.speed)))
    val courseRate   = times slopeAgainst courseAngles

    val diveAngles = points.toArrayBy(point => PerPointMetrics.diveAngle(point.speed))
    val diveRate   = times slopeAgainst diveAngles

    val speeds         = points.toArrayBy(_.speed.magnitude)
    val totalSpeedRate = times slopeAgainst speeds

    SlopeMetrics(acceleration, courseRate, diveRate, totalSpeedRate)

  /** Component-wise least-squares slopes of the speed over the window (m/s²). */
  def acceleration: GeoVector =
    slopeMetrics.acceleration

  /** Least-squares slope of the course over the window, unwrapped to a continuous signal (deg/s). */
  def courseRate: Double =
    slopeMetrics.courseRate

  /** Least-squares slope of the dive angle over the window (deg/s). */
  def diveRate: Double =
    slopeMetrics.diveRate

  /** Least-squares slope of the total speed over the window (m/s²). */
  def totalSpeedRate: Double =
    slopeMetrics.totalSpeedRate

  /** Instantaneous signed turn rate at the window center (deg/s); 0 for the first point or non-positive dt. */
  def turnRate(centerIndex: Int): Double =
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
  private def relativeSeconds: Array[Double] =
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

object WindowedMetrics {

  final private case class SlopeMetrics(
    acceleration: GeoVector,
    courseRate: Double,
    diveRate: Double,
    totalSpeedRate: Double,
  )

  def apply[A: PointReader](window: Vector[A]): WindowedMetrics[A] =
    new WindowedMetrics(window)

}
