package com.colofabrix.scala.beesight.detection

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.effect.unsafe.IORuntime
import cats.syntax.all.given
import com.colofabrix.scala.beesight.*
import com.colofabrix.scala.beesight.files.CsvFileOps
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
      val untaggedLines = loadSummary(resultsFile.toFile).filter(_.tag.isEmpty)

      val failures =
        untaggedLines
          .zipWithIndex
          .parFlatTraverse {
            case (expected, idx) =>
              val filename = expected.filename
              val filePath = flysightDir.resolve(filename)
              val points   = CsvFileOps.readCsv[FlysightPoint](filePath)

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
        info(s"Success rate is ${successRate}")
        info(s"Test failed:\n${failures.mkString("\n\n")}")
        successRate.should(be >= 0.95)
      }

    }

    "detect all flight stages correctly for PROBLEMATIC files" in {
      val untaggedLines = loadSummary(resultsFile.toFile).filter(_.tag.nonEmpty)

      val failures =
        untaggedLines
          .zipWithIndex
          .parFlatTraverse {
            case (expected, idx) =>
              val filename = expected.filename
              val filePath = flysightDir.resolve(filename)
              val points   = CsvFileOps.readCsv[FlysightPoint](filePath)

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
        info(s"Success rate is ${successRate}")
        info(s"Test failed:\n${failures.mkString("\n\n")}")
        successRate.should(be >= 0.30)
      }

    }

  }

  final private case class TestFileRow(
    filename: String,
    takeoff: Option[FlightPoint],
    freefall: Option[FlightPoint],
    canopy: Option[FlightPoint],
    landing: Option[FlightPoint],
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
          takeoff = parseOptLong(cols.lift(header("takeoff_pt"))).map(FlightPoint(_, 0.0)),
          freefall = parseOptLong(cols.lift(header("freefall_pt"))).map(FlightPoint(_, 0.0)),
          canopy = parseOptLong(cols.lift(header("canopy_pt"))).map(FlightPoint(_, 0.0)),
          landing = parseOptLong(cols.lift(header("landing_pt"))).map(FlightPoint(_, 0.0)),
          tag = cols.lift(header("tag")).filter(_.trim.nonEmpty),
        )
      }

  private def parseOptLong(value: Option[String]): Option[Long] =
    value.filter(_.trim.nonEmpty).map(_.trim.toLong)

}
