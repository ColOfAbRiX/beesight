package com.colofabrix.scala.beesight.swoop

import cats.effect.IO
import cats.syntax.all.given
import com.colofabrix.scala.beesight.*
import com.colofabrix.scala.beesight.TestFilesHandler.given
import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.jumpevents.JumpEventsDetector
import com.colofabrix.scala.beesight.matchers.SwoopMatchers
import com.colofabrix.scala.beesight.model.*
import java.nio.file.Paths
import org.scalatest.wordspec.AnyWordSpec
import scala.concurrent.duration.*

/*
FIXME: TEMPORARY diagnostic for BulkSwoopDetectionSpec - categorizes all failures. Delete after use.
*/
class SwoopDiagnosticsSpec extends AnyWordSpec with IOValues with SwoopMatchers {

  private val testFilesDir = Paths.get("src/test/resources/test_files")
  private val resultsFile  = Paths.get("src/test/resources/test_files/swoop_results.csv")
  private val config       = DetectionConfig.default

  "SwoopDataDetector diagnostics" should {

    "categorize all bulk failures" in {
      val expectedValues =
        TestFilesHandler
          .readCsv[GSwoopResult](resultsFile)
          .compile
          .toList
          .result(timeout = 1.minute)

      val results =
        expectedValues
          .zipWithIndex
          .parTraverse { case (expected, idx) =>
            categorize(idx, expected)
          }
          .result(timeout = 10.minutes)

      val categories = results.groupBy(_.category).view.mapValues(_.size).toList.sortBy(-_._2)
      println("\n===== FAILURE CATEGORIES =====")
      categories.foreach { case (cat, n) => println(f"  $cat%-22s $n%d") }

      println("\n===== DETAILS =====")
      results.sortBy(r => (r.category, r.idx)).foreach { r =>
        println(f"  [${r.idx}%3d] ${r.category}%-18s ${r.filename}: ${r.detail}")
      }
    }

  }

  //  Categorization  //

  private case class Result(idx: Int, filename: String, category: String, detail: String)

  private def categorize(idx: Int, expected: GSwoopResult): IO[Result] = {
    val filename = expected.filename
    val path     = testFilesDir.resolve(filename)

    val program =
      for
        events <- IO.defer(detectEvents(path))
        swoop  <- SwoopDataDetector(config).detect(events, TestFilesHandler.readCsv[DataPoint](path))
        matched = matchSwoop(expected)(swoop).matches
      yield (events, swoop, matched)

    program
      .map { case (events, swoop, matched) =>
        val category: String =
          (swoop, expected.isEmpty, matched, events.canopy, events.landing) match {
            case (None, false, _, None, _) | (None, false, _, _, None) => "MISSING-EVENTS"
            case (None, false, _, _, _)                                => "NO-VALID-DIVE"
            case (Some(_), true, _, _, _)                              => "FALSE-POSITIVE"
            case (_, _, true, _, _)                                    => "MATCH"
            case _                                                     => "MISMATCH"
          }

        val detail = (swoop, expected) match {
          case (Some(s), e) if !e.isEmpty =>
            val m = matchSwoop(expected)(Some(s))
            if m.matches then "ok"
            else m.failureMessage.replace("\n", " | ").trim
          case (Some(s), _) =>
            f"turnInit=${s.turnInitiation.altitude}%.0f maxVz=${s.maxVerticalSpeed.speed}%.1f deg=${s.degreesOfRotation}%.0f"
          case _ => ""
        }

        Result(idx, filename, category, detail)
      }
      .handleError { t => Result(idx, filename, "ERROR", t.getMessage) }
  }

  //  Pipeline helpers (same as the bulk spec)  //

  private def detectEvents(path: java.nio.file.Path): IO[FlightEvents] =
    TestFilesHandler
      .readCsv[DataPoint](path)
      .through(JumpEventsDetector(config).detectEvents)
      .compile
      .last
      .map(_.getOrElse(FlightEvents.empty))

}
