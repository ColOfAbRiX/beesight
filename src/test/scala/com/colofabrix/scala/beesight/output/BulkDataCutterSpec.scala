package com.colofabrix.scala.beesight.output

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.colofabrix.scala.beesight.Tools
import com.colofabrix.scala.beesight.config.*
import com.colofabrix.scala.beesight.config.DataCutterConfig.given
import com.colofabrix.scala.beesight.model.*
import java.io.File
import java.nio.file.Paths
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class BulkDataCutterSpec extends AnyWordSpec with Matchers {

  private val resultsFile =
    Paths.get("src/test/resources/bulk_detection_spec/data_cutter_results.csv").toFile

  private val config: Config =
    Config.default

  private val keepBefore: Int =
    config.dataCutterConfig.keepBeforeTakeoff.value

  private val keepAfter: Int =
    config.dataCutterConfig.keepAfterLanding.value

  private val minRetained: Double =
    config.dataCutterConfig.minRetainedPoints.value

  "DataCutter" should {
    val testRows = loadTestRows(resultsFile)

    testRows.foreach { row =>
      val expectedStart = computeExpectedStart(row)
      val expectedEnd   = computeExpectedEnd(row)
      val expectedSize  = expectedEnd - expectedStart

      val retainedRatio = expectedSize.toDouble / row.rowsCount

      val (finalExpectedStart, finalExpectedEnd, finalExpectedSize) =
        if retainedRatio < minRetained then (0, row.rowsCount, row.rowsCount)
        else (expectedStart, expectedEnd, expectedSize)

      s"correctly cut ${row.filename} (start=$finalExpectedStart, end=$finalExpectedEnd, size=$finalExpectedSize/${row.rowsCount})" in {
          val points = buildAnnotatedStream(row)
          val result = runCutter(points)

          withClue(s"${row.filename}: ") {
            result.size shouldBe finalExpectedSize

            if result.nonEmpty then {
              result.head.source shouldBe finalExpectedStart
              result.last.source shouldBe (finalExpectedEnd - 1)

              // Verify contiguous indices
              val indices = result.map(_.source)
              indices shouldBe (finalExpectedStart until finalExpectedEnd).toVector
            }
          }
        }
    }

  }

  // -- Helpers ----------------------------------------------------------------

  private def computeExpectedStart(row: TestRow): Int =
    row.takeoff match {
      case Some(t) => math.max(0, t - keepBefore)
      case None    => 0
    }

  private def computeExpectedEnd(row: TestRow): Int =
    row.landing match {
      case Some(l) => math.min(row.rowsCount, l + keepAfter + 1)
      case None    => row.rowsCount
    }

  private def buildAnnotatedStream(row: TestRow): Vector[OutputFlightPoint[Int]] =
    val takeoffEvent  = row.takeoff.map(i => FlightEventPoint(index = i.toLong, altitude = 0.0))
    val freefallEvent = row.freefall.map(i => FlightEventPoint(index = i.toLong, altitude = 0.0))
    val canopyEvent   = row.canopy.map(i => FlightEventPoint(index = i.toLong, altitude = 0.0))
    val landingEvent  = row.landing.map(i => FlightEventPoint(index = i.toLong, altitude = 0.0))

    (0 until row.rowsCount).map { i =>
      OutputFlightPoint[Int](
        takeoff = takeoffEvent,
        freefall = freefallEvent,
        canopy = canopyEvent,
        landing = landingEvent,
        source = i,
      )
    }.toVector

  private def runCutter(points: Vector[OutputFlightPoint[Int]]): Vector[OutputFlightPoint[Int]] =
    val cutter = DataCutter(config)
    fs2.Stream
      .emits[IO, OutputFlightPoint[Int]](points)
      .through(cutter.cutPipe[IO, Int])
      .compile
      .toVector
      .unsafeRunSync()

  // -- CSV loading ------------------------------------------------------------

  final private case class TestRow(
    filename: String,
    takeoff: Option[Int],
    freefall: Option[Int],
    canopy: Option[Int],
    landing: Option[Int],
    rowsCount: Int,
  )

  private def loadTestRows(file: File): List[TestRow] =
    Tools
      .simpleCsvRead(file) { (header, cols) =>
        val filename = cols(header("filename")).trim
        Option.when(filename.nonEmpty) {
          TestRow(
            filename = filename,
            takeoff = parseOptInt(cols.lift(header("takeoff_pt"))),
            freefall = parseOptInt(cols.lift(header("freefall_pt"))),
            canopy = parseOptInt(cols.lift(header("canopy_pt"))),
            landing = parseOptInt(cols.lift(header("landing_pt"))),
            rowsCount = cols(header("rows_count")).trim.toInt,
          )
        }
      }
      .flatten

  private def parseOptInt(value: Option[String]): Option[Int] =
    value.filter(_.trim.nonEmpty).map(_.trim.toInt)

}
