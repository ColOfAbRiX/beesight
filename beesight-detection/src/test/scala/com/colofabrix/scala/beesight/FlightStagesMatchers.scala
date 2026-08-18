package com.colofabrix.scala.beesight

import com.colofabrix.scala.beesight.model.*
import org.scalatest.matchers.{ Matcher, MatchResult }

/**
 * Custom ScalaTest matchers for FlightStagesPoints comparison with tolerances
 */
trait FlightStagesMatchers {

  private val tolerances: Map[String, Long] =
    Map(
      "takeoff"  -> 100,
      "freefall" -> 5,
      "canopy"   -> 10,
      "landing"  -> 50,
    )

  /**
   * Matches FlightStagesPoints against expected values with per-field tolerances
   */
  def matchStages(expected: FlightEvents): Matcher[FlightEvents] =
    (obtained: FlightEvents) => {
      val fieldResults =
        List(
          checkField("takeoff", obtained.takeoff.map(_.index), expected.takeoff.map(_.index)),
          checkField("freefall", obtained.freefall.map(_.index), expected.freefall.map(_.index)),
          checkField("canopy", obtained.canopy.map(_.index), expected.canopy.map(_.index)),
          checkField("landing", obtained.landing.map(_.index), expected.landing.map(_.index)),
        )

      val failures = fieldResults.collect { case (name, Some(error)) => s"      $name: $error" }
      val passes   = fieldResults.collect { case (name, None) => name }

      MatchResult(
        matches = failures.isEmpty,
        rawFailureMessage = s"\n    FlightStagesPoints did not match:\n${failures.mkString("\n")}",
        rawNegatedFailureMessage =
          s"FlightStagesPoints matched when it should not have. Matched: ${passes.mkString(", ")}",
      )
    }

  private def checkField(name: String, obtained: Option[Long], expected: Option[Long]): (String, Option[String]) =
    val tolerance = tolerances.getOrElse(name, 0L)

    val error =
      (obtained, expected) match {
        case (Some(o), Some(e)) if Math.abs(o - e) > tolerance =>
          Some(s"expected $e ± $tolerance, got $o (diff: ${o - e})")
        case (None, Some(e)) =>
          Some(s"expected $e ± $tolerance, got None")
        case (Some(o), None) =>
          Some(s"expected None, got $o")
        case _ =>
          None
      }

    (name, error)

}
