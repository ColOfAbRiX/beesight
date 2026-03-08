package com.colofabrix.scala.beesight.detection

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.effect.unsafe.IORuntime
import cats.syntax.all.given
import com.colofabrix.scala.beesight.*
import com.colofabrix.scala.beesight.input.CsvFileReader
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.model.formats.FlysightPoint
import java.io.File
import java.nio.file.Paths
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.concurrent.duration.*
import scala.io.Source

class BulkDetectionSpec extends AnyWordSpec with Matchers with IOConfigValues with FlightStagesMatchers {

  private val flysightDir =
    Paths.get("src/test/resources/bulk_detection_spec/flysight")

  private val resultsFile =
    Paths.get("src/test/resources/bulk_detection_spec/points_results.csv")

  "FlightStagesDetection" should {

    "detect all flight stages correctly for untagged files" in {
      val expectedSuccessRate = 99.0

      val untaggedLines = loadSummary(resultsFile.toFile).filter(_.tag.isEmpty)

      val failures =
        untaggedLines
          .zipWithIndex
          .parFlatTraverse {
            case (expected, idx) =>
              val filename = expected.filename
              val zipName  = filename.replaceAll("(?i)\\.csv$", ".zip")
              val filePath = flysightDir.resolve(zipName)
              val points   = CsvFileReader.readCsv[FlysightPoint](filePath)

              Tools
                .detectPoints(points)
                .map { result =>
                  val matcher     = matchStages(expected.toFlightEvents)
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

    "detect all flight stages correctly for PROBLEMATIC files" in {
      val expectedSuccessRate = 54.0

      val taggedLines = loadSummary(resultsFile.toFile).filter(_.tag.nonEmpty)

      val failures =
        taggedLines
          .zipWithIndex
          .parFlatTraverse {
            case (expected, idx) =>
              val filename = expected.filename
              val zipName  = filename.replaceAll("(?i)\\.csv$", ".zip")
              val filePath = flysightDir.resolve(zipName)
              val points   = CsvFileReader.readCsv[FlysightPoint](filePath)

              Tools
                .detectPoints(points)
                .map { result =>
                  val matcher     = matchStages(expected.toFlightEvents)
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

  final private case class TestFileRow(
    filename: String,
    takeoff: Option[FlightEventPoint],
    freefall: Option[FlightEventPoint],
    canopy: Option[FlightEventPoint],
    landing: Option[FlightEventPoint],
    tag: Option[String],
  ) {
    def toFlightEvents: FlightEvents =
      FlightEvents(takeoff, freefall, canopy, landing)
  }

  private def loadSummary(file: File): List[TestFileRow] =
    val lines =
      Source
        .fromFile(resultsFile.toFile)
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
          takeoff = parseOptLong(cols.lift(header("takeoff_pt"))).map(FlightEventPoint(_, 0.0)),
          freefall = parseOptLong(cols.lift(header("freefall_pt"))).map(FlightEventPoint(_, 0.0)),
          canopy = parseOptLong(cols.lift(header("canopy_pt"))).map(FlightEventPoint(_, 0.0)),
          landing = parseOptLong(cols.lift(header("landing_pt"))).map(FlightEventPoint(_, 0.0)),
          tag = cols.lift(header("tag")).filter(_.trim.nonEmpty),
        )
      }

  private def parseOptLong(value: Option[String]): Option[Long] =
    value.filter(_.trim.nonEmpty).map(_.trim.toLong)

}
