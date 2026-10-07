package com.colofabrix.scala.beesight.swoop

import cats.effect.IO
import cats.syntax.all.given
import com.colofabrix.scala.beesight.*
import com.colofabrix.scala.beesight.TestFilesHandler.given
import com.colofabrix.scala.beesight.config.BeesightConfig
import com.colofabrix.scala.beesight.jumpevents.JumpEventsDetector
import com.colofabrix.scala.beesight.matchers.SwoopMatchers
import com.colofabrix.scala.beesight.model.*
import java.nio.file.Paths
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.concurrent.duration.*

class SwoopAnalyserBulkSpec extends AnyWordSpec with Matchers with IOValues with SwoopMatchers {

  private val testFilesDir =
    Paths.get("src/test/resources/test_files")

  private val resultsFile =
    Paths.get("src/test/resources/test_files/swoop_results.csv")

  "SwoopAnalyser" should {

    "match the reference metrics for all test recordings" in {
      val expectedValues =
        TestFilesHandler
          .readCsv[SwoopResult](resultsFile)
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
                  val matcher     = matchSwoop(expected, degreesTolerance = 10.0)
                  val matchResult = matcher(actual)

                  if (!matchResult.matches) {
                    List(s"  [$idx] $filename${matchResult.failureMessage}")
                  } else {
                    Nil
                  }
                }
          }
          .result(timeout = 5.minutes)

      val total       = expectedValues.size
      val failed      = failures.size
      val successRate = 1.0 - (failed.toDouble / total.toDouble)

      if (failed > 0) then
        info(f"Success rate is ${successRate * 100.0}%.1f%%")
        info(f"    Total number of files: $total")
        info(f"    Failed files:          $failed\n")
        info(s"Failing tests summary:\n${failures.mkString("\n\n")}")

      successRate shouldBe 1.0
    }

  }

  private val referenceConfig =
    val rotationConfig =
      BeesightConfig
        .default
        .swoop
        .rotation
        .copy(trimEnabled = false)

    val swoopConfig =
      BeesightConfig
        .default
        .swoop
        .copy(
          rotation = rotationConfig
        )

    BeesightConfig
      .default
      .copy(swoop = swoopConfig)

  private def detectSwoop(filePath: java.nio.file.Path): IO[Option[SwoopData]] =
    for {
      events  <- detectEvents(filePath)
      detector = SwoopAnalyser(referenceConfig.swoop)
      swoop   <- detector.analyse(events, TestFilesHandler.readCsv[CsvPoint](filePath))
    } yield swoop

  private def detectEvents(filePath: java.nio.file.Path): IO[FlightEvents] =
    TestFilesHandler
      .readCsv[CsvPoint](filePath)
      .through(JumpEventsDetector(BeesightConfig.default.jumpevents).detect)
      .compile
      .last
      .map(_.getOrElse(FlightEvents.empty))

}
