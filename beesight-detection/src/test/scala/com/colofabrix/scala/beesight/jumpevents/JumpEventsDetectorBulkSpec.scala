package com.colofabrix.scala.beesight.jumpevents

import cats.effect.IO
import cats.syntax.all.given
import com.colofabrix.scala.beesight.*
import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.model.*
import java.io.File
import java.nio.file.Paths
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.concurrent.duration.*
import scala.io.Source
import com.colofabrix.scala.beesight.matchers.JumpEventMatchers

class JumpEventsDetectorBulkSpec extends AnyWordSpec with Matchers with IOValues with JumpEventMatchers {

  private val testFilesDir =
    Paths.get("src/test/resources/test_files")

  private val resultsFile =
    Paths.get("src/test/resources/test_files/jump_events_results.csv")

  "JumpEventsDetector" should {

    "detect all flight events correctly for untagged files" in {
      val expectedSuccessRate = 99.0

      val untaggedLines = loadSummary(resultsFile.toFile).filter(_.tag.isEmpty)

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
                    Nil
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

      val taggedLines = loadSummary(resultsFile.toFile).filter(_.tag.nonEmpty)

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
                    Nil
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
    FlysightTestReader
      .readCsvStream(filePath)
      .through(JumpEventsDetector(DetectionConfig.default).streamDetect)
      .compile
      .last
      .map {
        _.fold(FlightEvents.empty) { output =>
          FlightEvents(
            takeoff = output.takeoff,
            freefall = output.freefall,
            canopy = output.canopy,
            landing = output.landing,
          )
        }
      }

  // CSV summary loading

  final private case class TestFileRow(
    filename: String,
    takeoff: Option[EventPoint],
    freefall: Option[EventPoint],
    canopy: Option[EventPoint],
    landing: Option[EventPoint],
    tag: Option[String],
  ) {

    def toFlightEvents: FlightEvents =
      FlightEvents(takeoff, freefall, canopy, landing)

  }

  private def loadSummary(file: File): List[TestFileRow] =
    val lines =
      Source
        .fromFile(file)
        .getLines()
        .toList
        .filterNot(_.isBlank)

    val header =
      lines
        .head
        .split(",")
        .zipWithIndex
        .toMap

    lines
      .tail
      .filter(_.trim.nonEmpty)
      .map { line =>
        val cols     = line.split(",", -1)
        val filename = cols(header("filename"))

        TestFileRow(
          filename = filename,
          takeoff = parseOptLong(cols.lift(header("takeoff_pt"))).map(EventPoint(_, 0.0)),
          freefall = parseOptLong(cols.lift(header("freefall_pt"))).map(EventPoint(_, 0.0)),
          canopy = parseOptLong(cols.lift(header("canopy_pt"))).map(EventPoint(_, 0.0)),
          landing = parseOptLong(cols.lift(header("landing_pt"))).map(EventPoint(_, 0.0)),
          tag = cols.lift(header("tag")).filter(_.trim.nonEmpty),
        )
      }

  private def parseOptLong(value: Option[String]): Option[Long] =
    value.filter(_.trim.nonEmpty).map(_.trim.toLong)

}
