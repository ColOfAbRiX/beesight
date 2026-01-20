package com.colofabrix.scala.beesight.detection

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.colofabrix.scala.beesight.config.Config
import com.colofabrix.scala.beesight.*
import com.colofabrix.scala.beesight.files.CsvFileOps
import com.colofabrix.scala.beesight.model.*
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
      val lines     = Source.fromFile(resultsFile.toFile).getLines().toList.filterNot(_.isBlank)
      val header    = lines.head.split(",").zipWithIndex.toMap
      val dataLines = lines.tail.filter(_.trim.nonEmpty)

      val untaggedLines =
        dataLines.drop(1).filter { line =>
          val cols = line.split(",", -1)
          val tag  = cols.lift(header("tag")).getOrElse("").trim
          tag.isEmpty
        }

      forEvery(untaggedLines) { line =>
        val cols     = line.split(",", -1)
        val filename = cols(header("filename"))

        val expected =
          FlightStagesPoints(
            takeoff = parseOptLong(cols.lift(header("takeoff_pt"))).map(FlightStagePoint(_, 0.0)),
            freefall = parseOptLong(cols.lift(header("freefall_pt"))).map(FlightStagePoint(_, 0.0)),
            canopy = parseOptLong(cols.lift(header("canopy_pt"))).map(FlightStagePoint(_, 0.0)),
            landing = parseOptLong(cols.lift(header("landing_pt"))).map(FlightStagePoint(_, 0.0)),
            lastPoint = 0,
            isValid = true,
          )

        val filePath = flysightDir.resolve(filename)
        val points   = CsvFileOps.readCsv[FlysightPoint](filePath)
        val result   = detectPoints(points).result()

        withClue(s"INSPECTING FILE: $filename\n") {
          result.should(matchStages(expected))
        }
      }
    }

  }

  private def parseOptLong(value: Option[String]): Option[Long] =
    value.filter(_.trim.nonEmpty).map(_.trim.toLong)

  private def detectPoints(data: fs2.Stream[IOConfig, FlysightPoint]): IOConfig[FlightStagesPoints] =
    data
      .through(FlightStagesDetection.streamDetectA)
      .compile
      .last
      .map {
        _.fold(FlightStagesPoints.empty) { output =>
          FlightStagesPoints(
            takeoff = output.takeoff,
            freefall = output.freefall,
            canopy = output.canopy,
            landing = output.landing,
            lastPoint = output.lastPoint,
            isValid = output.isValid,
          )
        }
      }

}
