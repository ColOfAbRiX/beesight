package com.colofabrix.scala.beesight.detection

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.colofabrix.scala.beesight.*
import com.colofabrix.scala.beesight.config.Config
import com.colofabrix.scala.beesight.files.CsvFileOps
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.model.formats.FlysightPoint
import java.io.File
import java.nio.file.Paths
import org.scalatest.Inspectors.forEvery
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.io.Source

class FlightStagesDetectionSpec extends AnyWordSpec with Matchers with IOConfigValues with FlightStagesMatchers {

  private val flysightDir = Paths.get("src/test/resources/flysight")
  private val resultsFile = Paths.get("src/test/resources/points_results.csv")

  "FlightStagesDetection" should {

    "detect all flight stages correctly for untagged files" in {
      val untaggedLines = loadSummary(resultsFile.toFile).filter(_.tag.isEmpty)

      forEvery(untaggedLines) { expected =>
        val filename = expected.file.getName()
        val filePath = flysightDir.resolve(filename)
        val points   = CsvFileOps.readCsv[FlysightPoint](filePath)
        val result   = detectPoints(points).result()

        withClue(s"\nInspecting file: $filename\n") {
          result.should(matchStages(expected.toFlightEvents))
        }
      }
    }

  }

  private final case class TestFileRow(
    file: File,
    takeoff: Option[FlightPoint],
    freefall: Option[FlightPoint],
    canopy: Option[FlightPoint],
    landing: Option[FlightPoint],
    tag: Option[String],
  ) {
    def toFlightEvents: FlightEvents =
      FlightEvents(takeoff, freefall, canopy, landing, lastPoint = -1)
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
          file = file,
          takeoff = parseOptLong(cols.lift(header("takeoff_pt"))).map(FlightPoint(_, 0.0)),
          freefall = parseOptLong(cols.lift(header("freefall_pt"))).map(FlightPoint(_, 0.0)),
          canopy = parseOptLong(cols.lift(header("canopy_pt"))).map(FlightPoint(_, 0.0)),
          landing = parseOptLong(cols.lift(header("landing_pt"))).map(FlightPoint(_, 0.0)),
          tag = cols.lift(header("tag")),
        )
      }

  private def parseOptLong(value: Option[String]): Option[Long] =
    value.filter(_.trim.nonEmpty).map(_.trim.toLong)

  private def detectPoints(data: fs2.Stream[IOConfig, FlysightPoint]): IOConfig[FlightEvents] =
    data
      .through(FlightStagesDetection.streamDetectA)
      .compile
      .last
      .map {
        _.fold(FlightEvents.empty) { output =>
          FlightEvents(
            takeoff = output.takeoff,
            freefall = output.freefall,
            canopy = output.canopy,
            landing = output.landing,
            lastPoint = output.lastPoint,
          )
        }
      }

}
