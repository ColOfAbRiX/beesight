package com.colofabrix.scala.beesight.metrics

import com.colofabrix.scala.beesight.math.Heading
import com.colofabrix.scala.beesight.model.FlightMetric
import org.scalatest.matchers.{ Matcher, MatchResult }

/**
 * Field-by-field comparison of a [[FlightMetric]] against the golden metrics export, converting the export's
 * display units (feet, mph) to SI and comparing the course through its wrapped angular difference.
 */
trait MetricsMatchers {

  private val FtToM   = 0.3048
  private val MphToMs = 1.0 / 2.23694

  /** Tolerances absorb the export's 6-decimal rounding; identical computations agree well below them. */
  private val lengthTolerance = 1e-5 // m, after conversion
  private val speedTolerance  = 1e-5 // m/s, after conversion
  private val angleTolerance  = 1e-4 // deg
  private val ratioTolerance  = 1e-4
  // J/kg and J/kg/s
  private val coefficientTolerance = 1e-3

  /** Field-level comparison: one failure message per field outside its tolerance. */
  def checkFlightMetric(obtained: FlightMetric, expected: FlySightMetricsResult): List[String] =
    List(
      check(
        "Elevation",
        "m",
        obtained.altitude,
        expected.elevation.map(_ * FtToM),
        lengthTolerance
      ),
      check(
        "Vertical speed",
        "m/s",
        obtained.verticalSpeed,
        expected.verticalSpeed.map(_ * MphToMs),
        speedTolerance
      ),
      check(
        "Horizontal speed",
        "m/s",
        obtained.horizontalSpeed,
        expected.horizontalSpeed.map(_ * MphToMs),
        speedTolerance
      ),
      check(
        "Total speed",
        "m/s",
        obtained.totalSpeed,
        expected.totalSpeed.map(_ * MphToMs),
        speedTolerance
      ),
      check(
        "Dive angle",
        "deg",
        obtained.diveAngle,
        expected.diveAngle,
        angleTolerance
      ),
      check(
        "Dive rate",
        "deg/s",
        obtained.diveRate,
        expected.diveRate,
        angleTolerance
      ),
      check(
        "Glide ratio",
        "",
        obtained.glideRatio,
        expected.glideRatio,
        ratioTolerance
      ),
      check(
        "Horizontal accuracy",
        "m",
        obtained.horizontalAccuracy,
        expected.horizontalAccuracy.map(_ * FtToM),
        lengthTolerance
      ),
      check(
        "Vertical accuracy",
        "m",
        obtained.verticalAccuracy,
        expected.verticalAccuracy.map(_ * FtToM),
        lengthTolerance
      ),
      check(
        "Speed accuracy",
        "m/s",
        obtained.speedAccuracy,
        expected.speedAccuracy.map(_ * MphToMs),
        speedTolerance
      ),
      check(
        "Satellites",
        "",
        obtained.satellites.map(_.toDouble),
        expected.satellites.map(_.toDouble),
        1e-9
      ),
      check(
        "Total energy",
        "J/kg",
        obtained.totalEnergy,
        expected.totalEnergy,
        lengthTolerance
      ),
      check(
        "Energy rate",
        "J/kg/s",
        obtained.energyRate,
        expected.energyRate,
        speedTolerance
      ),
      check(
        "Lift coefficient",
        "",
        obtained.liftCoefficient,
        expected.liftCoefficient,
        coefficientTolerance
      ),
      check(
        "Drag coefficient",
        "",
        obtained.dragCoefficient,
        expected.dragCoefficient,
        coefficientTolerance
      ),
      checkAngle("Course", "deg", obtained.course, expected.course, angleTolerance),
      check(
        "Course rate",
        "deg/s",
        obtained.courseRate,
        expected.courseRate,
        angleTolerance
      ),
      check(
        "Course accuracy",
        "deg",
        obtained.courseAccuracyEstimate,
        expected.courseAccuracy.map(Math.toDegrees),
        angleTolerance
      ),
      check(
        "Acceleration Forward",
        "m/s²",
        obtained.accelerationAlongTrack,
        expected.accelerationForward,
        speedTolerance
      ),
      check(
        "Acceleration Right",
        "m/s²",
        obtained.accelerationCrossTrack,
        expected.accelerationRight,
        speedTolerance
      ),
      check(
        "Acceleration Down",
        "m/s²",
        obtained.acceleration.vertical,
        expected.accelerationDown,
        speedTolerance
      ),
      check(
        "Acceleration Magnitude",
        "m/s²",
        obtained.accelerationMagnitude,
        expected.accelerationMagnitude,
        speedTolerance
      ),
      check(
        "Spherical error probable",
        "m",
        obtained.sphericalErrorProbably,
        expected.sphericalErrorProbable,
        lengthTolerance
      ),
      check(
        "Speed score accuracy",
        "m/s",
        obtained.speedScoreAccuracy,
        expected.speedScoreAccuracy,
        speedTolerance
      ),
    ).flatten

  /** Matcher wrapper around [[checkFlightMetric]] for use in point-wise assertions. */
  def matchFlightMetric(expected: FlySightMetricsResult): Matcher[FlightMetric] =
    (obtained: FlightMetric) =>
      val failures = checkFlightMetric(obtained, expected)
      MatchResult(
        matches = failures.isEmpty,
        rawFailureMessage = s"\n  Flight metrics did not match:\n${failures.mkString("\n")}",
        rawNegatedFailureMessage = "Flight metrics matched when they should not have.",
      )

  private def check(
    name: String,
    unit: String,
    obtained: Option[Double],
    expected: Option[Double],
    tolerance: Double
  ): Option[String] =
    (obtained, expected) match
      case (Some(o), Some(e)) => checkValue(name, unit, o, e, tolerance)
      case (None, None)       => None
      case (o, e)             => Some(s"  $name: presence mismatch (obtained ${o.isDefined}, expected ${e.isDefined})")

  private def check(
    name: String,
    unit: String,
    obtained: Double,
    expected: Option[Double],
    tolerance: Double
  ): Option[String] =
    expected match
      case None        => None
      case Some(value) => checkValue(name, unit, obtained, value, tolerance)

  private def checkValue(
    name: String,
    unit: String,
    obtained: Double,
    expected: Double,
    tolerance: Double
  ): Option[String] =
    val diff = Math.abs(obtained - expected)
    Option.when(diff > tolerance):
      f"  $name: expected $expected%.9f $unit, got $obtained%.9f (diff $diff%.3e)"

  /** Compares two angles through their wrapped difference, so the [0, 360) vs (-180, 180] conventions do not matter. */
  private def checkAngle(
    name: String,
    unit: String,
    obtained: Double,
    expected: Option[Double],
    tolerance: Double
  ): Option[String] =
    expected match
      case None        => None
      case Some(value) => checkAngle(name, unit, Some(obtained), Some(value), tolerance)

  private def checkAngle(
    name: String,
    unit: String,
    obtained: Option[Double],
    expected: Option[Double],
    tolerance: Double
  ): Option[String] =
    (obtained, expected) match
      case (Some(o), Some(e)) =>
        val diff = Math.abs(Heading.wrapSigned(o - e))
        Option.when(diff > tolerance):
          f"  $name: expected $e%.9f $unit, got $o%.9f (wrapped diff $diff%.3e)"
      case (None, None) =>
        None
      case (o, e) =>
        Some(s"  $name: presence mismatch (obtained ${o.isDefined}, expected ${e.isDefined})")

}
