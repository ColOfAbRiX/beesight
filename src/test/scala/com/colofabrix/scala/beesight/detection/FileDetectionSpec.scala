package com.colofabrix.scala.beesight.detection

import com.colofabrix.scala.beesight.*
import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.input.CsvFileReader
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.model.formats.FlysightPoint
import java.io.File
import java.nio.file.Paths
import java.util.zip.ZipInputStream
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.io.Source

class FileDetectionSpec extends AnyWordSpec with Matchers with IOConfigValues {

  private val inputData =
    Paths.get("src/test/resources/file_detection_spec/input_data.zip")

  "FlightStagesDetection" should {

    "output the same number of rows as the input" in {
      val inputLineCount = countCsvLinesInZip(inputData.toFile)

      val processedResults =
        CsvFileReader
          .readCsv[FlysightPoint](inputData)
          .through(FlightStagesDetection(DetectionConfig.default).streamDetect)
          .compile
          .toList
          .result()

      processedResults.size shouldBe inputLineCount
    }

    "produce phases in monotonically non-decreasing sequence order" in {
      val processedResults =
        CsvFileReader
          .readCsv[FlysightPoint](inputData)
          .through(FlightStagesDetection(DetectionConfig.default).streamDetect)
          .compile
          .toList
          .result()

      processedResults
        .sliding(2)
        .foreach {
          case Seq(prev, curr) =>
            withClue(s"Phase ${prev.phase} -> ${curr.phase}: ") {
              curr.phase.sequence should be >= prev.phase.sequence
            }
          case _ =>
        }
    }

    "detect all expected flight phases" in {
      val processedResults =
        CsvFileReader
          .readCsv[FlysightPoint](inputData)
          .through(FlightStagesDetection(DetectionConfig.default).streamDetect)
          .compile
          .toList
          .result()

      val phases = processedResults.map(_.phase).distinct

      phases should contain(FlightPhase.BeforeTakeoff)
      phases should contain(FlightPhase.Climbing)
      phases should contain(FlightPhase.Freefall)
      phases should contain(FlightPhase.UnderCanopy)
      phases should contain(FlightPhase.Landed)
    }

    "start with BeforeTakeoff phase" in {
      val processedResults =
        CsvFileReader
          .readCsv[FlysightPoint](inputData)
          .through(FlightStagesDetection(DetectionConfig.default).streamDetect)
          .compile
          .toList
          .result()

      processedResults.head.phase shouldBe FlightPhase.BeforeTakeoff
    }

    "end with Landed phase" in {
      val processedResults =
        CsvFileReader
          .readCsv[FlysightPoint](inputData)
          .through(FlightStagesDetection(DetectionConfig.default).streamDetect)
          .compile
          .toList
          .result()

      processedResults.last.phase shouldBe FlightPhase.Landed
    }

  }

  private def countCsvLinesInZip(file: File): Int =
    val zis = new ZipInputStream(java.io.FileInputStream(file))
    try {
      zis.getNextEntry
      Source
        .fromInputStream(zis)
        .getLines()
        .drop(1)
        .size
    } finally
      zis.close()

}
