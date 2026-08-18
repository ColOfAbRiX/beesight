package com.colofabrix.scala.beesight.detection

import com.colofabrix.scala.beesight.*
import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.input.CsvFileReader
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.model.formats.{ FileFormatAdapter, FlysightPoint }
import java.io.File
import java.nio.file.Paths
import java.util.zip.ZipInputStream
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.io.Source

class FileDetectionSpec extends AnyWordSpec with Matchers with IOConfigValues {

  private val inputData =
    Paths.get("src/test/resources/file_detection_spec/input_data.zip")

  private def detectAll: List[FlightEvents] =
    CsvFileReader
      .readCsv[FlysightPoint](inputData)
      .map(FileFormatAdapter[FlysightPoint].toDataPoint)
      .through(FlightStagesDetection(DetectionConfig.default).streamDetect)
      .compile
      .toList
      .result()

  "FlightStagesDetection" should {

    "output the same number of rows as the input" in {
      val inputLineCount = countCsvLinesInZip(inputData.toFile)

      val processedResults = detectAll

      processedResults.size shouldBe inputLineCount
    }

    "detect all expected flight phases" in {
      val events = detectAll.lastOption.getOrElse(FlightEvents.empty)

      events.takeoff shouldBe defined
      events.freefall shouldBe defined
      events.canopy shouldBe defined
      events.landing shouldBe defined
    }

    "detect phases in the correct chronological order" in {
      val events = detectAll.lastOption.getOrElse(FlightEvents.empty)

      val indices =
        List(events.takeoff, events.freefall, events.canopy, events.landing).flatten.map(_.index)

      indices shouldBe indices.sorted
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
