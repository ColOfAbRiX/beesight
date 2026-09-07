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
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.concurrent.duration.*

class BulkSwoopDetectionSpec extends AnyWordSpec with Matchers with IOValues with SwoopMatchers {

  private val testFilesDir =
    Paths.get("src/test/resources/test_files")

  private val resultsFile =
    Paths.get("src/test/resources/test_files/swoop_results.csv")

  "SwoopDataDetector" should {

    "match gswoop metrics for all test recordings" in {
      val expectedValues =
        TestFilesHandler
          .readCsv[GSwoopResult](resultsFile)
          .compile
          .toList
          .result()

      val failures =
        expectedValues
          .zipWithIndex
          .parFlatTraverse {
            case (expected, idx) =>
              val filename = expected.filename
              val filePath = testFilesDir.resolve(filename)

              detectSwoop(filePath)
                .map { actual =>
                  val matcher     = matchSwoop(expected)
                  val matchResult = matcher(actual)

                  if (!matchResult.matches) {
                    List(s"  [$idx] $filename${matchResult.failureMessage}")
                  } else {
                    Nil
                  }
                }
          }
          .result(timeout = 5.minutes)

      if (failures.nonEmpty) {
        val total       = expectedValues.size
        val failed      = failures.size
        val successRate = 1.0 - (failed.toDouble / total.toDouble)

        info(f"Success rate is ${successRate * 100.0}%.1f%%")
        info(f"    Total number of files: $total")
        info(f"    Failed files:          $failed\n")
        info(s"Failing tests summary:\n${failures.mkString("\n\n")}")

        successRate shouldBe 1.0
      }

    }

  }

  private def detectSwoop(filePath: java.nio.file.Path): IO[Option[SwoopData]] =
    for {
      events  <- detectEvents(filePath)
      detector = SwoopDataDetector(DetectionConfig.default)
      swoop   <- detector.detect(events, TestFilesHandler.readCsv[DataPoint](filePath))
    } yield swoop

  private def detectEvents(filePath: java.nio.file.Path): IO[FlightEvents] =
    TestFilesHandler
      .readCsv[DataPoint](filePath)
      .through(JumpEventsDetector(DetectionConfig.default).streamDetect)
      .compile
      .last
      .map(_.getOrElse(FlightEvents.empty))

}
