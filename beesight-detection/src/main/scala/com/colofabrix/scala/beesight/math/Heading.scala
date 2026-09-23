package com.colofabrix.scala.beesight.math

import com.colofabrix.scala.beesight.model.GeoVector
import java.lang.Math.*

/**
 * Heading computation utilities for swoop metrics.
 */
private[beesight] object Heading {

  /**
   * Computes the horizontal heading in degrees from a velocity vector.
   *
   * @return Heading in degrees [0, 360), where 0° = North, 90° = East
   */
  def headingDegrees(speed: GeoVector): Double =
    val h = toDegrees(atan2(speed.east, speed.north))
    if h < 0 then h + 360.0
    else h

  /**
   * Computes the shortest angular distance between two headings, handling 0°/360° wrapping.
   *
   * @return Absolute angular distance in degrees [0, 180]
   */
  def angularDistance(h1: Double, h2: Double): Double =
    abs(((h2 - h1 + 180.0) % 360.0 + 360.0) % 360.0 - 180.0)

  /**
   * Wraps an angle delta to the signed range [-180, 180].
   *
   * @return The equivalent delta in [-180, 180]
   */
  def wrapSigned(delta: Double): Double =
    if delta > 180.0 then delta - 360.0
    else if delta < -180.0 then delta + 360.0
    else delta

  /**
   * Computes cumulative absolute heading change across a sequence of points.
   *
   * Points with horizontal speed below `minHorizontalSpeed` are skipped to avoid
   * noisy heading values at near-zero ground speeds.
   *
   * @param points             Sequence of velocity vectors
   * @param minHorizontalSpeed Minimum horizontal speed (m/s) to include a point in heading computation
   * @return Total cumulative heading change in degrees
   */
  def cumulativeHeadingChange(points: Vector[GeoVector], minHorizontalSpeed: Double): Double =
    val filtered = points.filter(_.horizontal >= minHorizontalSpeed)

    if filtered.length < 2 then
      0.0
    else
      filtered
        .tail
        .foldLeft((heading = headingDegrees(filtered.head), result = 0.0)) {
          case ((prevHeading, total), speed) =>
            val h = headingDegrees(speed)
            (h, total + angularDistance(prevHeading, h))
        }
        .result

}
