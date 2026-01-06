package com.colofabrix.scala.beesight

import cats.effect.unsafe.implicits.global
import com.colofabrix.scala.beesight.files.CsvFileOps
import com.colofabrix.scala.beesight.model.FlysightPoint
import java.nio.file.Paths
import munit.*
import scala.io.Source

class FlightStagesDetectionSpec extends FunSuite {

  private val flysightDir = Paths.get("src/test/resources/flysight")
  private val resultsFile = Paths.get("src/test/resources/points_results.csv")

  test("detect all flight stages correctly for untagged files") {
    val lines     = Source.fromFile(resultsFile.toFile).getLines().toList
    val header    = lines.head.split(",").zipWithIndex.toMap
    val dataLines = lines.tail.filter(_.trim.nonEmpty)

    for (line <- dataLines) {
      val cols     = line.split(",", -1) // -1 to keep trailing empty strings
      val filename = cols(header("filename"))
      val tag      = cols.lift(header("tag")).getOrElse("").trim

      // Skip rows with tags
      if (tag.isEmpty) {
        val expectedTakeoff  = parseOptLong(cols.lift(header("takeoff_pt")))
        val expectedFreefall = parseOptLong(cols.lift(header("freefall_pt")))
        val expectedCanopy   = parseOptLong(cols.lift(header("canopy_pt")))
        val expectedLanding  = parseOptLong(cols.lift(header("landing_pt")))

        val filePath = flysightDir.resolve(filename)
        val points   = CsvFileOps.readCsv[FlysightPoint](filePath)
        val result   = FlightStagesDetection.detect(points).unsafeRunSync()

        assertRange(result.takeoff.map(_.lineIndex), expectedTakeoff, 50, s"Takeoff mismatch for $filename")
        assertRange(result.freefall.map(_.lineIndex), expectedFreefall, 5, s"Freefall mismatch for $filename")
        assertRange(result.canopy.map(_.lineIndex), expectedCanopy, 10, s"Canopy mismatch for $filename")
        assertRange(result.landing.map(_.lineIndex), expectedLanding, 50, s"Landing mismatch for $filename")
      }
    }
  }

  private def parseOptLong(value: Option[String]): Option[Long] =
    value.filter(_.trim.nonEmpty).map(_.trim.toLong)

  import munit.internal.console.StackTraces

  private def assertRange(
    optObtained: Option[Long],
    optExpected: Option[Long],
    range: Long,
    clue: => Any = "values are not the same",
  ): Unit =
    StackTraces.dropInside {
      (optObtained, optExpected) match {
        case (Some(obtained), Some(expected)) =>
          if obtained > (expected + range) then
            fail(s"The value ${obtained} is above the exepected MAX value of ${expected + range} - ${clue}")
          else if obtained < (expected - range) then
            fail(s"The value ${obtained} is below the expected MIN value of ${expected - range} - ${clue}")
        case (None, None) =>
        case _ =>
          fail(s"One value is present the other is not - $clue")
      }
    }

}
