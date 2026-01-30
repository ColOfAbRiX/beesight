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
import scala.io.Source

class FileDetectionSpec extends AnyWordSpec with Matchers with IOConfigValues {

  "FlightStagesDetection" should {

    "output the Phases at the point where they happen" in {
      val inputData    = Paths.get("src/test/resources/file_detection_spec/input_data.csv")
      val expectedData = Paths.get("src/test/resources/file_detection_spec/expected_data.csv")

      val expectedPhases =
        Source
          .fromFile(expectedData.toFile)
          .getLines()
          .drop(1) // Skip header
          .map { line =>
            val cols = line.split(",")
            cols.last.trim
          }
          .toList

      val processedResults =
        CsvFileOps
          .readCsv[FlysightPoint](inputData)
          .through(FlightStagesDetection.streamDetectA)
          .compile
          .toList
          .result()

      processedResults.size shouldBe expectedPhases.size

      processedResults.zip(expectedPhases).zipWithIndex.foreach {
        case ((result, expectedPhase), idx) =>
          withClue(s"Row $idx (time: ${result.source.time}): ") {
            result.phase.toString shouldBe expectedPhase
          }
      }
    }

  }

}
