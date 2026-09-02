package com.colofabrix.scala.beesight.matchers

import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.swoop.*
import org.scalatest.matchers.{ Matcher, MatchResult }

/**
 * Custom ScalaTest matchers comparing [[SwoopData]] against gswoop-derived expected values.
 *
 * The comparison is done at the object level: each expected metric present in the CSV is checked against the computed
 * value with its own tolerance, and all failures are reported together.
 */
trait SwoopMatchers {

  private val altitudeTolerance = 25.0 // m
  private val speedTolerance    = 2.0  // m/s
  private val rotationTolerance = 30.0 // degrees
  private val timeTolerance     = 1.5  // s

  /**
   * Matches the swoop metrics computed from a recording against the expected gswoop values.
   *
   * If the expected row is empty (no metrics), the obtained metrics must be `None`. Otherwise the obtained metrics must
   * be defined and every expected field must match within tolerance; expected fields left empty are not compared.
   */
  def matchSwoop(expected: GSwoopResult): Matcher[Option[SwoopData]] =
    (obtained: Option[SwoopData]) => {
      val failures =
        (obtained, expected) match {
          case (None, e) if e.isEmpty =>
            Nil
          case (None, _) =>
            List("      swoop: expected some metrics, got None")
          case (Some(_), e) if e.isEmpty =>
            List("      swoop: expected no metrics, got defined result")
          case (Some(swoop), e) =>
            checkFields(swoop, e)
        }

      MatchResult(
        matches = failures.isEmpty,
        rawFailureMessage = s"\n    Swoop metrics did not match:\n${failures.mkString("\n")}",
        rawNegatedFailureMessage = "Swoop metrics matched when they should not have.",
      )
    }

  private def checkFields(swoop: SwoopData, expected: GSwoopResult): List[String] =
    List(
      check("turnInitiation.altitude", swoop.turnInitiation.altitude, expected.turnInitiationAltitude, altitudeTolerance),
      check("rolloutStart.altitude", swoop.rolloutStart.altitude, expected.rolloutStartAltitude, altitudeTolerance),
      check("maxVerticalSpeed", swoop.maxVerticalSpeed, expected.maxVerticalSpeed, speedTolerance),
      check("maxTotalSpeed", swoop.maxTotalSpeed, expected.maxTotalSpeed, speedTolerance),
      check("maxHorizontalSpeed", swoop.maxHorizontalSpeed, expected.maxHorizontalSpeed, speedTolerance),
      check("degreesOfRotation", swoop.degreesOfRotation, expected.degreesOfRotation, rotationTolerance),
      check(
        "timeToExecuteTurn",
        swoop.timeToExecuteTurn.toMillis / 1000.0,
        expected.timeToExecuteTurn,
        timeTolerance,
      ),
    ).flatten

  private def check(name: String, obtained: Double, expected: Option[Double], tolerance: Double): Option[String] =
    expected match {
      case Some(e) if Math.abs(obtained - e) > tolerance =>
        Some(s"      $name: expected $e ± $tolerance, got $obtained (diff: ${obtained - e})")
      case _ =>
        None
    }

}
