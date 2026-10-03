package com.colofabrix.scala.beesight.jumpevents

import cats.effect.IO
import cats.syntax.all.given
import com.colofabrix.scala.beesight.*
import com.colofabrix.scala.beesight.TestFilesHandler.given
import com.colofabrix.scala.beesight.config.BeesightConfig
import com.colofabrix.scala.beesight.matchers.JumpEventMatchers
import com.colofabrix.scala.beesight.model.*
import java.nio.file.Paths
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.concurrent.duration.*

class JumpEventsDetectorBulkSpec extends AnyWordSpec with Matchers with IOValues with JumpEventMatchers {

  private val testFilesDir =
    Paths.get("src/test/resources/test_files")

  private val resultsFile =
    Paths.get("src/test/resources/test_files/jump_events_results.csv")

  "JumpEventsDetector" should {

    "detect all flight events correctly for untagged files" in {
      val expectedSuccessRate = 99.0

      val untaggedLines =
        TestFilesHandler
          .readCsv[JumpEventsResult](resultsFile)
          .streamResult()
          .filter(_.tag.isEmpty)

      val failures =
        untaggedLines
          .zipWithIndex
          .parFlatTraverse {
            case (expected, idx) =>
              val filename = expected.filename
              val zipName  = filename.replaceAll("(?i)\\.csv$", ".zip")
              val filePath = testFilesDir.resolve(zipName)

              detectLastEvents(filePath)
                .map { result =>
                  val matcher     = matchEvents(expected.toFlightEvents)
                  val matchResult = matcher(result)

                  if (!matchResult.matches) {
                    List(s"  [$idx] $filename${matchResult.failureMessage}")
                  } else {
                    List.empty
                  }
                }
          }
          .result(timeout = 5.minutes)

      if (failures.nonEmpty) {
        val successRate = 1.0 - (failures.size.toDouble / untaggedLines.size.toDouble)

        info(f"Success rate is ${successRate * 100.0}%.1f%%")
        info(f"    Total number of files: ${untaggedLines.size}")
        info(f"    Failed files:          ${failures.size}\n")
        info(s"Failing tests summary:\n${failures.mkString("\n\n")}")

        successRate.should(be >= expectedSuccessRate / 100.0)
      }

    }

    "detect all flight events correctly for PROBLEMATIC files" in {
      val expectedSuccessRate = 54.0

      val taggedLines =
        TestFilesHandler
          .readCsv[JumpEventsResult](resultsFile)
          .streamResult()
          .filter(_.tag.nonEmpty)

      val failures =
        taggedLines
          .zipWithIndex
          .parFlatTraverse {
            case (expected, idx) =>
              val filename = expected.filename
              val zipName  = filename.replaceAll("(?i)\\.csv$", ".zip")
              val filePath = testFilesDir.resolve(zipName)

              detectLastEvents(filePath)
                .map { result =>
                  val matcher     = matchEvents(expected.toFlightEvents)
                  val matchResult = matcher(result)

                  if (!matchResult.matches) {
                    List(s"  [$idx] $filename${matchResult.failureMessage}")
                  } else {
                    List.empty
                  }
                }
          }
          .result(timeout = 5.minutes)

      if (failures.nonEmpty) {
        val successRate = 1.0 - (failures.size.toDouble / taggedLines.size.toDouble)

        info(f"Success rate is ${successRate * 100.0}%.1f%%")
        info(f"    Total number of files: ${taggedLines.size}")
        info(f"    Failed files:          ${failures.size}\n")
        info(s"Failing tests summary:\n${failures.mkString("\n\n")}")

        successRate.should(be >= expectedSuccessRate / 100.0)
      }

    }

  }

  private def detectLastEvents(filePath: java.nio.file.Path): IO[FlightEvents] =
    TestFilesHandler
      .readCsv[CsvPoint](filePath)
      .through(JumpEventsDetector(BeesightConfig.default.jumpevents).detect)
      .compile
      .last
      .map {
        case Some(events) => events
        case None         => FlightEvents.empty
      }

}
