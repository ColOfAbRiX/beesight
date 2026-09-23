package com.colofabrix.scala.beesight.metrics

import com.colofabrix.scala.beesight.math.Heading
import com.colofabrix.scala.beesight.model.GeoVector
import java.lang.Math.*

/**
 * Per-point flight metrics, computable from a single data point without any windowing.
 */
private[metrics] object PerPointMetrics {

  /** Angle between the flight path and the horizon (deg); positive when descending. */
  def diveAngle(speed: GeoVector): Double =
    toDegrees(atan2(speed.vertical, speed.horizontal))

  /** Horizontal distance covered per unit of descent; 0 when the vertical speed is zero. */
  def glideRatio(speed: GeoVector): Double =
    if speed.vertical == 0.0 then
      0.0
    else
      speed.horizontal / speed.vertical

  /** Direction of travel (deg, [0, 360); 0° = North, 90° = East). */
  def course(speed: GeoVector): Double =
    Heading.headingDegrees(speed)

  /** Specific mechanical energy: kinetic plus potential (J/kg). */
  def totalEnergy(altitude: Double, totalSpeed: Double): Double =
    totalSpeed * totalSpeed / 2.0 + MetricsConstants.Gravity * altitude

  /** Acceleration component along the direction of travel, "Forward" (m/s²); 0 when not moving horizontally. */
  def accelerationAlongTrack(acceleration: GeoVector, speed: GeoVector): Double =
    if speed.horizontal == 0.0 then
      0.0
    else
      (acceleration.north * speed.north + acceleration.east * speed.east) / speed.horizontal

  /** Acceleration component to the right of the direction of travel, "Right" (m/s²); 0 when not moving horizontally. */
  def accelerationCrossTrack(acceleration: GeoVector, speed: GeoVector): Double =
    if speed.horizontal == 0.0 then
      0.0
    else
      (acceleration.east * speed.north - acceleration.north * speed.east) / speed.horizontal

}
