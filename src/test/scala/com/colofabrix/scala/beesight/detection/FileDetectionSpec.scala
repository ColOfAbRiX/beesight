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
import java.util.zip.ZipInputStream
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.io.Source
import com.colofabrix.scala.beesight.config.DetectionConfig

class FileDetectionSpec extends AnyWordSpec with Matchers with IOConfigValues {

  private val inputData = Paths.get("src/test/resources/file_detection_spec/input_data.zip")

  "FlightStagesDetection" should {

    "output the same number of rows as the input" in {
      val inputLineCount = countCsvLinesInZip(inputData.toFile)

      val processedResults =
        CsvFileOps
          .readCsv[FlysightPoint](inputData)
          .through(FlightStagesDetection(DetectionConfig.default).streamDetect)
          .compile
          .toList
          .result()

      processedResults.size shouldBe inputLineCount
    }

    "output the Phases at the point where they happen" in {
      val processedResults =
        CsvFileOps
          .readCsv[FlysightPoint](inputData)
          .through(FlightStagesDetection(DetectionConfig.default).streamDetect)
          .compile
          .toList
          .result()

      processedResults
        .zipWithIndex
        .sliding(2)
        .foreach {
          case Seq((prev, _), (curr, idx)) =>
            if (prev.phase != curr.phase) {
              withClue(s"Phase transition at row $idx, from ${prev.phase} to ${curr.phase}: ") {
                curr.phaseTransitionIndex shouldBe idx
              }
            }
          case _ =>
          // Handle edge case of single element
        }
    }

    "produce phases in monotonically non-decreasing sequence order" in {
      val processedResults =
        CsvFileOps
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
        CsvFileOps
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
        CsvFileOps
          .readCsv[FlysightPoint](inputData)
          .through(FlightStagesDetection(DetectionConfig.default).streamDetect)
          .compile
          .toList
          .result()

      processedResults.head.phase shouldBe FlightPhase.BeforeTakeoff
    }

    "end with Landed phase" in {
      val processedResults =
        CsvFileOps
          .readCsv[FlysightPoint](inputData)
          .through(FlightStagesDetection(DetectionConfig.default).streamDetect)
          .compile
          .toList
          .result()

      processedResults.last.phase shouldBe FlightPhase.Landed
    }

  }

  /** Reads the first entry from a zip file and counts the non-header CSV lines */
  private def countCsvLinesInZip(file: File): Int =
    val zis = new ZipInputStream(java.io.FileInputStream(file))
    try {
      zis.getNextEntry
      Source
        .fromInputStream(zis)
        .getLines()
        .drop(1)
        .size
    } finally {
      zis.close()
    }


}
