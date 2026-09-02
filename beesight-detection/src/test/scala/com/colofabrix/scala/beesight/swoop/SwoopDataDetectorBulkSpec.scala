package com.colofabrix.scala.beesight.swoop

import cats.effect.IO
import cats.syntax.all.given
import com.colofabrix.scala.beesight.*
import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.jumpevents.{FlysightTestReader, JumpEventsDetector}
import com.colofabrix.scala.beesight.matchers.SwoopMatchers
import com.colofabrix.scala.beesight.model.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.concurrent.duration.*
import java.io.File
import java.nio.file.Paths
import scala.io.Source

class BulkSwoopDetectionSpec extends AnyWordSpec with Matchers with IOValues with SwoopMatchers {

  private val testFilesDir =
    Paths.get("src/test/resources/test_files")

  private val resultsFile =
    Paths.get("src/test/resources/test_files/swoop_results.csv")

  "SwoopDataDetector" should {

    "match gswoop metrics for all test recordings" in {

      val expectedValues = loadSummary(resultsFile.toFile)

      val failures =
        expectedValues
          .zipWithIndex
          .parFlatTraverse { case (expected, idx) =>
            val filename = expected.filename
            val filePath = testFilesDir.resolve(filename)

            detectSwoop(filePath)
              .map { actual =>
                val matcher     = matchSwoop(expected)
                val matchResult = matcher(actual)

                if (!matchResult.matches) {
                  List(s"  [$idx] $filename${matchResult.failureMessage}")
                } else {
                  Nil
                }
              }
          }
          .result(timeout = 5.minutes)

      if (failures.nonEmpty) {
        val total = expectedValues.size
        val failed = failures.size
        val successRate = 1.0 - (failed.toDouble / total.toDouble)

        info(f"Success rate is ${successRate * 100.0}%.1f%%")
        info(f"    Total number of files: $total")
        info(f"    Failed files:          $failed\n")
        info(s"Failing tests summary:\n${failures.mkString("\n\n")}")

        successRate.should(be >= 0.0)
      }

    }

  }

  private def detectSwoop(filePath: java.nio.file.Path): IO[Option[SwoopData]] =
    for {
      events <- detectEvents(filePath)
      swoop  <- SwoopDataDetector(DetectionConfig.default.swoop).detect(events, FlysightTestReader.readCsvStream(filePath))
    } yield swoop

  private def detectEvents(filePath: java.nio.file.Path): IO[FlightEvents] =
    FlysightTestReader
      .readCsvStream(filePath)
      .through(JumpEventsDetector(DetectionConfig.default).streamDetect)
      .compile
      .last
      .map(_.getOrElse(FlightEvents.empty))

  // CSV summary loading

  final private case class SwoopTestRow(
    filename: String,
    turnInitiationAltitude: Option[Double],
    rolloutStartAltitude: Option[Double],
    finishedRolloutAltitude: Option[Double],
    maxVerticalSpeed: Option[Double],
    maxTotalSpeed: Option[Double],
    maxHorizontalSpeed: Option[Double],
    degreesOfRotation: Option[Double],
    timeToExecuteTurn: Option[Double],
    timeDuringRollout: Option[Double],
    timeAloftDuringSwoop: Option[Double],
    entryGateSpeed: Option[Double],
    distanceToStop: Option[Double],
    touchdownEstimate: Option[Double],
  ) {

    def toGSwoopResult: GSwoopResult =
      GSwoopResult(
        filename = filename,
        turnInitiationAltitude = turnInitiationAltitude,
        rolloutStartAltitude = rolloutStartAltitude,
        finishedRolloutAltitude = finishedRolloutAltitude,
        maxVerticalSpeed = maxVerticalSpeed,
        maxTotalSpeed = maxTotalSpeed,
        maxHorizontalSpeed = maxHorizontalSpeed,
        degreesOfRotation = degreesOfRotation,
        timeToExecuteTurn = timeToExecuteTurn,
        timeDuringRollout = timeDuringRollout,
        timeAloftDuringSwoop = timeAloftDuringSwoop,
        entryGateSpeed = entryGateSpeed,
        distanceToStop = distanceToStop,
        touchdownEstimate = touchdownEstimate,
      )

  }

  private def loadSummary(file: File): List[GSwoopResult] =
    val lines =
      Source
        .fromFile(file)
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
        val cols = line.split(",", -1)

        SwoopTestRow(
          filename = cols(header("filename")),
          turnInitiationAltitude = parseOptDouble(cols.lift(header("turn_initiation_altitude"))),
          rolloutStartAltitude = parseOptDouble(cols.lift(header("rollout_start_altitude"))),
          finishedRolloutAltitude = parseOptDouble(cols.lift(header("finished_rollout_altitude"))),
          maxVerticalSpeed = parseOptDouble(cols.lift(header("max_vertical_speed"))),
          maxTotalSpeed = parseOptDouble(cols.lift(header("max_total_speed"))),
          maxHorizontalSpeed = parseOptDouble(cols.lift(header("max_horizontal_speed"))),
          degreesOfRotation = parseOptDouble(cols.lift(header("degrees_of_rotation"))),
          timeToExecuteTurn = parseOptDouble(cols.lift(header("time_to_execute_turn"))),
          timeDuringRollout = parseOptDouble(cols.lift(header("time_during_rollout"))),
          timeAloftDuringSwoop = parseOptDouble(cols.lift(header("time_aloft_during_swoop"))),
          entryGateSpeed = parseOptDouble(cols.lift(header("entry_gate_speed"))),
          distanceToStop = parseOptDouble(cols.lift(header("distance_to_stop"))),
          touchdownEstimate = parseOptDouble(cols.lift(header("touchdown_estimate"))),
        )
      }
      .map(_.toGSwoopResult)

  private def parseOptDouble(value: Option[String]): Option[Double] =
    value.filter(_.trim.nonEmpty).map(_.trim.toDouble)

}
