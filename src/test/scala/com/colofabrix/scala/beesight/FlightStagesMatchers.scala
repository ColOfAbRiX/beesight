package com.colofabrix.scala.beesight

import com.colofabrix.scala.beesight.model.*
import org.scalatest.matchers.{ Matcher, MatchResult }

/**
 * Custom ScalaTest matchers for FlightStagesPoints comparison with tolerances
 */
trait FlightStagesMatchers {

  private val tolerances: Map[String, Long] =
    Map(
      "takeoff"  -> 50,
      "freefall" -> 5,
      "canopy"   -> 10,
      "landing"  -> 50,
    )

  /**
   * Matches FlightStagesPoints against expected values with per-field tolerances
   */
  def matchStages(expected: FlightStagesPoints): Matcher[FlightStagesPoints] =
    (obtained: FlightStagesPoints) => {
      val fieldResults =
        List(
          checkField("takeoff", obtained.takeoff.map(_.lineIndex), expected.takeoff.map(_.lineIndex)),
          checkField("freefall", obtained.freefall.map(_.lineIndex), expected.freefall.map(_.lineIndex)),
          checkField("canopy", obtained.canopy.map(_.lineIndex), expected.canopy.map(_.lineIndex)),
          checkField("landing", obtained.landing.map(_.lineIndex), expected.landing.map(_.lineIndex)),
        )

      val failures = fieldResults.collect { case (name, Some(error)) => s"  $name: $error" }
      val passes   = fieldResults.collect { case (name, None) => name }

      MatchResult(
        matches = failures.isEmpty,
        rawFailureMessage = s"FlightStagesPoints did not match:\n${failures.mkString("\n")}",
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
