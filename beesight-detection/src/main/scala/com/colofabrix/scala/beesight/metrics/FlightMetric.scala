package com.colofabrix.scala.beesight.metrics

import com.colofabrix.scala.beesight.model.GeoVector
import java.time.Instant

/**
 * The flight metrics computed for a single data point
 * plot.
 *
 * The vertical component of speed and acceleration is positive downward, angles are in degrees with the course 
 * normalised to [0, 360), and the windowed rates are least-squares slopes over a clamped ±`slopeHalfWindow` window of 
 * points (see [[FlightMetricsCalculator]]).
 *
 * @param time                   Timestamp of the data point
 * @param altitude               Altitude above ground level (m); see [[MetricsGround]]
 * @param verticalSpeed          Vertical speed (m/s), positive downward
 * @param horizontalSpeed        Magnitude of the horizontal speed (m/s)
 * @param totalSpeed             3D speed magnitude (m/s)
 * @param course                 Direction of travel (deg, [0, 360); 0° = North, 90° = East)
 * @param courseRate             Least-squares slope of the course over the window, unwrapped across north (deg/s)
 * @param courseAccuracy         GPS course accuracy (deg), when the format provides it; falls back to [[courseAccuracyEstimate]]
 * @param courseAccuracyEstimate Course accuracy derived from the speed accuracy
 * @param glideRatio             Horizontal distance covered per unit of descent; 0 when not descending
 * @param liftCoefficient        Lift coefficient from the gravity-corrected acceleration across the flight path; 0 when not moving
 * @param dragCoefficient        Drag coefficient from the gravity-corrected acceleration along the flight path; 0 when not moving
 * @param diveAngle              Angle between the flight path and the horizon (deg); positive when descending
 * @param diveRate               Least-squares slope of the dive angle over the window (deg/s)
 * @param turnRate               Instantaneous signed turn rate against the previous point (deg/s)
 * @param speedAccuracy          GPS speed accuracy (m/s), when the format provides it
 * @param horizontalAccuracy     GPS horizontal accuracy (m), when the format provides it
 * @param verticalAccuracy       GPS vertical accuracy (m), when the format provides it
 * @param satellites             Number of satellites used in the GPS fix, when the format provides it
 * @param acceleration           Least-squares slopes of the speed components over the window (m/s²)
 * @param accelerationMagnitude  Magnitude of the acceleration (m/s²)
 * @param accelerationAlongTrack Acceleration component along the direction of travel, "Forward" (m/s²)
 * @param accelerationCrossTrack Acceleration component to the right of the direction of travel, "Right" (m/s²)
 * @param totalEnergy            Specific mechanical energy: kinetic plus potential (J/kg)
 * @param energyRate             Rate of change of the specific mechanical energy (J/kg/s)
 * @param sphericalErrorProbably Spherical error probable: 3D 95th-percentile position error estimate (m)
 * @param speedScoreAccuracy     Accuracy of the 3-second speed-score average (m/s)
 */
final case class FlightMetric(
  time: Instant,
  altitude: Double,
  verticalSpeed: Double,
  horizontalSpeed: Double,
  totalSpeed: Double,
  course: Double,
  courseRate: Double,
  courseAccuracy: Option[Double],
  courseAccuracyEstimate: Option[Double],
  glideRatio: Double,
  liftCoefficient: Double,
  dragCoefficient: Double,
  diveAngle: Double,
  diveRate: Double,
  turnRate: Double,
  speedAccuracy: Option[Double],
  horizontalAccuracy: Option[Double],
  verticalAccuracy: Option[Double],
  satellites: Option[Int],
  acceleration: GeoVector,
  accelerationMagnitude: Double,
  accelerationAlongTrack: Double,
  accelerationCrossTrack: Double,
  totalEnergy: Double,
  energyRate: Double,
  sphericalErrorProbably: Option[Double],
  speedScoreAccuracy: Option[Double],
)
