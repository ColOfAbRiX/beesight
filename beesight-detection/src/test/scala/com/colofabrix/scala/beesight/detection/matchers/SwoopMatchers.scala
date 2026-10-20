package com.colofabrix.scala.beesight.detection.matchers

import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.detection.swoop.*
import org.scalatest.matchers.{ MatchResult, Matcher }

/**
 * Custom ScalaTest matchers comparing [[SwoopData]] against the expected values from the reference recordings.
 */
trait SwoopMatchers {

  private val altitudeTolerance = 25.0 // m
  private val speedTolerance    = 2.0  // m/s
  private val timeTolerance     = 1.5  // s

  private val MpsToKmh = 3.6

  /**
   * Matches the swoop metrics computed from a recording against the expected values.
   */
  def matchSwoop(expected: SwoopResult, degreesTolerance: Double = 0.0): Matcher[Option[SwoopData]] =
    (obtained: Option[SwoopData]) => {
      val failures =
        (obtained, expected) match {
          case (None, e) if e.isEmpty =>
            Nil
          case (None, _) =>
            List("  Swoop: expected metrics, got None")
          case (Some(_), e) if e.isEmpty =>
            List("  Swoop: expected None, got metrics")
          case (Some(swoop), e) =>
            checkFields(swoop, e, degreesTolerance)
        }

      MatchResult(
        matches = failures.isEmpty,
        rawFailureMessage = s"\n  Swoop metrics did not match:\n${failures.mkString("\n")}",
        rawNegatedFailureMessage = "Swoop metrics matched when they should not have.",
      )
    }

  /**
   * Matches only the expected angle of rotation from the logbook.
   */
  def matchSwoopAngle(expected: SwoopResult, angleTolerance: Double = 0.0): Matcher[Option[SwoopData]] =
    (obtained: Option[SwoopData]) => {
      val failures =
        (obtained, expected.expectedAngle) match {
          case (None, Some(_)) =>
            List("  Swoop: expected angle, got no swoop")
          case (Some(swoop), Some(_)) =>
            check(
              "Expected angle of rotation",
              "deg",
              Math.abs(swoop.degreesOfRotation.value),
              expected.expectedAngle,
              angleTolerance
            ).toList
          case _ =>
            Nil
        }

      MatchResult(
        matches = failures.isEmpty,
        rawFailureMessage = s"\n  Swoop angle did not match:\n${failures.mkString("\n")}",
        rawNegatedFailureMessage = "Swoop angle matched when it should not have.",
      )
    }

  private def checkFields(swoop: SwoopData, expected: SwoopResult, degreesTolerance: Double): List[String] =
    List(
      check(
        "Turn initiation altitude",
        "m",
        swoop.turnInitiation.altitude,
        expected.turnInitiationAltitude,
        altitudeTolerance
      ),
      check(
        "Rollout start altitude",
        "m",
        swoop.rolloutStart.altitude,
        expected.rolloutStartAltitude,
        altitudeTolerance
      ),
      check(
        "Max vertical speed",
        "km/h",
        swoop.maxVerticalSpeed.speed.value * MpsToKmh,
        expected.maxVerticalSpeed.map(_ * MpsToKmh),
        speedTolerance * MpsToKmh,
        Some(swoop.maxVerticalSpeed.altitude.value)
      ),
      check(
        "Max total speed",
        "km/h",
        swoop.maxTotalSpeed.speed.value * MpsToKmh,
        expected.maxTotalSpeed.map(_ * MpsToKmh),
        speedTolerance * MpsToKmh,
        Some(swoop.maxTotalSpeed.altitude.value)
      ),
      check(
        "Max horizontal speed",
        "km/h",
        swoop.maxHorizontalSpeed.speed.value * MpsToKmh,
        expected.maxHorizontalSpeed.map(_ * MpsToKmh),
        speedTolerance * MpsToKmh,
        Some(swoop.maxHorizontalSpeed.altitude.value)
      ),
      check(
        "Time to execute turn",
        "s",
        swoop.timeToExecuteTurn.toMillis / 1000.0,
        expected.timeToExecuteTurn,
        timeTolerance,
      ),
      check(
        "Degrees of rotation",
        "deg",
        Math.abs(swoop.degreesOfRotation.value),
        expected.degreesOfRotation.map(v => Math.abs(v)),
        degreesTolerance,
      ),
    ).flatten

  private def check(
    name: String,
    unit: String,
    obtained: Double,
    expected: Option[Double],
    tolerance: Double,
    atAltitude: Option[Double] = None
  ): Option[String] =
    expected match {
      case Some(e) if Math.abs(obtained - e) > tolerance =>
        val diff = obtained - e
        val at   = atAltitude.fold("")(a => f" @ $a%.0f m")
        Some(f"  $name%-30s  expected $e%.1f $unit, got $obtained%.1f $unit$at  (diff: $diff%+.1f, tol: +/- $tolerance%.1f)")
      case _ =>
        None
    }

}
