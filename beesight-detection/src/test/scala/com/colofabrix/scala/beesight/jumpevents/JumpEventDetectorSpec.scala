package com.colofabrix.scala.beesight.jumpevents

import com.colofabrix.scala.beesight.IOValues
import com.colofabrix.scala.beesight.TestFilesHandler
import com.colofabrix.scala.beesight.TestFilesHandler.given
import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.model.*
import java.nio.file.Paths
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class JumpEventsDetectorSpec extends AnyWordSpec with Matchers with IOValues {

  private val inputData =
    Paths.get("src/test/resources/test_files/csv_files/jump_event_detector_file.zip")

  "JumpEventsDetector" should {

    "output the same number of rows as the input" in {
      val processedResults = processInput
      val inputLineCount = TestFilesHandler.countLines(inputData)

      processedResults.size shouldBe inputLineCount
    }

    "produce phases in monotonically non-decreasing sequence order" in {
      val processedResults = processInput

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
      val processedResults = processInput

      val phases = processedResults.map(toPhase).distinct

      phases should contain(FlightPhase.BeforeTakeoff)
      phases should contain(FlightPhase.Climbing)
      phases should contain(FlightPhase.Freefall)
      phases should contain(FlightPhase.UnderCanopy)
      phases should contain(FlightPhase.Landed)
    }

    "start with BeforeTakeoff phase" in {
      val processedResults = processInput

      toPhase(processedResults.head) shouldBe FlightPhase.BeforeTakeoff
    }

    "end with Landed phase" in {
      val processedResults = processInput

      toPhase(processedResults.last) shouldBe FlightPhase.Landed
    }

  }

  private def toPhase(events: FlightEvents): FlightPhase =
    if (events.landing.isDefined) FlightPhase.Landed
    else if (events.canopy.isDefined) FlightPhase.UnderCanopy
    else if (events.freefall.isDefined) FlightPhase.Freefall
    else if (events.takeoff.isDefined) FlightPhase.Climbing
    else FlightPhase.BeforeTakeoff

  private def processInput: List[FlightEvents] =
    TestFilesHandler
      .readCsv[DataPoint](inputData)
      .through(JumpEventsDetector(DetectionConfig.default).streamDetect)
      .streamResult()

}
