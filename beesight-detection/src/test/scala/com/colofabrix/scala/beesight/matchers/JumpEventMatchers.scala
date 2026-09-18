package com.colofabrix.scala.beesight.matchers

import com.colofabrix.scala.beesight.model.*
import org.scalatest.matchers.{ Matcher, MatchResult }

/**
 * Custom ScalaTest matchers for FlightEvents comparison with tolerances
 */
trait JumpEventMatchers {

  private val tolerances: Map[String, Long] =
    Map(
      "takeoff"  -> 100,
      "freefall" -> 5,
      "canopy"   -> 10,
      "landing"  -> 50,
    )

  /**
   * Matches FlightEvents against expected values with per-field tolerances
   */
  def matchEvents(expected: FlightEvents): Matcher[FlightEvents] =
    (obtained: FlightEvents) => {
      val fieldResults =
        List(
          checkField("Takeoff", obtained.takeoff.map(_.index), expected.takeoff.map(_.index)),
          checkField("Freefall", obtained.freefall.map(_.index), expected.freefall.map(_.index)),
          checkField("Canopy", obtained.canopy.map(_.index), expected.canopy.map(_.index)),
          checkField("Landing", obtained.landing.map(_.index), expected.landing.map(_.index)),
        )

      val failures = fieldResults.collect { case (name, Some(error)) => s"  $name: $error" }

      MatchResult(
        matches = failures.isEmpty,
        rawFailureMessage = s"\n  Flight events did not match:\n${failures.mkString("\n")}",
        rawNegatedFailureMessage = "Flight events matched when they should not have.",
      )
    }

  private def checkField(name: String, obtained: Option[Long], expected: Option[Long]): (String, Option[String]) =
    val tolerance = tolerances.getOrElse(name.toLowerCase, 0L)

    val error =
      (obtained, expected) match {
        case (Some(o), Some(e)) if Math.abs(o - e) > tolerance =>
          val diff = o - e
          Some(f"expected $e +/- $tolerance, got $o (diff: $diff%d)")
        case (None, Some(e)) =>
          Some(s"expected $e +/- $tolerance, got None")
        case (Some(o), None) =>
          Some(s"expected None, got $o")
        case _ =>
          None
      }

    (name, error)

}
