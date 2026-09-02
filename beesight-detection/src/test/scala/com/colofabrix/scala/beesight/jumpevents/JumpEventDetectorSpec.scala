package com.colofabrix.scala.beesight.jumpevents

import com.colofabrix.scala.beesight.IOValues
import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.model.*
import java.io.File
import java.nio.file.Paths
import java.util.zip.ZipInputStream
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.io.Source

class JumpEventsDetectorSpec extends AnyWordSpec with Matchers with IOValues {

  private val inputData =
    Paths.get("src/test/resources/test_files/csv_files/jump_event_detector_file.zip")

  "JumpEventsDetector" should {

    "output the same number of rows as the input" in {
      val inputLineCount = countCsvLinesInZip(inputData.toFile)

      val processedResults =
        FlysightTestReader
          .readCsvStream(inputData)
          .through(JumpEventsDetector(DetectionConfig.default).streamDetect)
          .compile
          .toList
          .result()

      processedResults.size shouldBe inputLineCount
    }

    "produce phases in monotonically non-decreasing sequence order" in {
      val processedResults =
        FlysightTestReader
          .readCsvStream(inputData)
          .through(JumpEventsDetector(DetectionConfig.default).streamDetect)
          .compile
          .toList
          .result()

      processedResults
        .sliding(2)
        .foreach {
          case Seq(prev, curr) =>
            withClue(s"Phase ${toPhase(prev)} -> ${toPhase(curr)}: ") {
              toPhase(curr).sequence should be >= toPhase(prev).sequence
            }
          case _ =>
            ()
        }
    }

    "detect all expected flight phases" in {
      val processedResults =
        FlysightTestReader
          .readCsvStream(inputData)
          .through(JumpEventsDetector(DetectionConfig.default).streamDetect)
          .compile
          .toList
          .result()

      val phases = processedResults.map(toPhase).distinct

      phases should contain(FlightPhase.BeforeTakeoff)
      phases should contain(FlightPhase.Climbing)
      phases should contain(FlightPhase.Freefall)
      phases should contain(FlightPhase.UnderCanopy)
      phases should contain(FlightPhase.Landed)
    }

    "start with BeforeTakeoff phase" in {
      val processedResults =
        FlysightTestReader
          .readCsvStream(inputData)
          .through(JumpEventsDetector(DetectionConfig.default).streamDetect)
          .compile
          .toList
          .result()

      toPhase(processedResults.head) shouldBe FlightPhase.BeforeTakeoff
    }

    "end with Landed phase" in {
      val processedResults =
        FlysightTestReader
          .readCsvStream(inputData)
          .through(JumpEventsDetector(DetectionConfig.default).streamDetect)
          .compile
          .toList
          .result()

      toPhase(processedResults.last) shouldBe FlightPhase.Landed
    }

  }

  private def toPhase(events: FlightEvents): FlightPhase =
    if (events.landing.isDefined) FlightPhase.Landed
    else if (events.canopy.isDefined) FlightPhase.UnderCanopy
    else if (events.freefall.isDefined) FlightPhase.Freefall
    else if (events.takeoff.isDefined) FlightPhase.Climbing
    else FlightPhase.BeforeTakeoff

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
