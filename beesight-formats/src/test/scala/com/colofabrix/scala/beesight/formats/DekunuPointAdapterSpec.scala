package com.colofabrix.scala.beesight.formats

import cats.data.NonEmptyList
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.{ IOValues, TestFilesHandler }
import fs2.data.csv.*
import java.nio.file.Paths
import java.time.Instant
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class DekunuPointAdapterSpec extends AnyFreeSpec with Matchers with IOValues {

  private val tolerance = 1e-6

  private val dekunuReader = DekunuPoint.dekunuPointReader

  "DekunuPoint PointReader" - {

    "field mapping" - {

      "should map Unix epoch time to Instant" in {
        val point = dekunuPoint(gpsTime = 1718454600L)
        dekunuReader.time(point) shouldBe Instant.ofEpochSecond(1718454600L)
      }

      "should map gpsAltitudeMeters to altitude" in {
        val point = dekunuPoint(gpsAltitudeMeters = 3500.0)
        dekunuReader.altitude(point) shouldBe 3500.0
      }

      "should convert speed from knots to m/s" in {
        val point = dekunuPoint(gpsSpeedKnot = 1.0, gpsAngleDegree = 0.0)
        dekunuReader.speed(point).north shouldBe (0.51444444 +- tolerance)
      }

      "should map missing vertical speed to zero" in {
        val point = dekunuPoint(instVertSpeed = None)
        dekunuReader.speed(point).vertical shouldBe 0.0
      }

      "should map vertical speed preserving the downward-positive convention" in {
        val point = dekunuPoint(instVertSpeed = Some(-10.0))
        dekunuReader.speed(point).vertical shouldBe -10.0
      }

    }

    "heading decomposition into north and east" - {

      "heading 0 deg (north) should produce full north, zero east" in {
        val point = dekunuPoint(gpsSpeedKnot = 100.0, gpsAngleDegree = 0.0)
        val speed = dekunuReader.speed(point)
        speed.north shouldBe (51.444444 +- tolerance)
        speed.east shouldBe (0.0 +- tolerance)
      }

      "heading 90 deg (east) should produce zero north, full east" in {
        val point = dekunuPoint(gpsSpeedKnot = 100.0, gpsAngleDegree = 90.0)
        val speed = dekunuReader.speed(point)
        speed.north shouldBe (0.0 +- tolerance)
        speed.east shouldBe (51.444444 +- tolerance)
      }

      "heading 180 deg (south) should produce negative north, zero east" in {
        val point = dekunuPoint(gpsSpeedKnot = 100.0, gpsAngleDegree = 180.0)
        val speed = dekunuReader.speed(point)
        speed.north shouldBe (-51.444444 +- tolerance)
        speed.east shouldBe (0.0 +- tolerance)
      }

      "heading 270 deg (west) should produce zero north, negative east" in {
        val point = dekunuPoint(gpsSpeedKnot = 100.0, gpsAngleDegree = 270.0)
        val speed = dekunuReader.speed(point)
        speed.north shouldBe (0.0 +- tolerance)
        speed.east shouldBe (-51.444444 +- tolerance)
      }

      "heading 45 deg (northeast) should split speed equally between north and east" in {
        val point    = dekunuPoint(gpsSpeedKnot = 100.0, gpsAngleDegree = 45.0)
        val speed    = dekunuReader.speed(point)
        val expected = 51.444444 * Math.cos(Math.toRadians(45.0))
        speed.north shouldBe (expected +- tolerance)
        speed.east shouldBe (expected +- tolerance)
      }

    }

    "zero speed" - {

      "should produce zero velocity regardless of heading" in {
        val point = dekunuPoint(gpsSpeedKnot = 0.0, gpsAngleDegree = 123.0)
        val speed = dekunuReader.speed(point)
        speed.north shouldBe (0.0 +- tolerance)
        speed.east shouldBe (0.0 +- tolerance)
      }

    }

    "magnitude preservation" - {

      "horizontal magnitude should equal the converted speed for any heading" in {
        val headings = List(0.0, 30.0, 45.0, 90.0, 135.0, 180.0, 225.0, 270.0, 315.0)

        headings.foreach { heading =>
          val point = dekunuPoint(gpsSpeedKnot = 50.0, gpsAngleDegree = heading)
          val speed = dekunuReader.speed(point)

          withClue(s"heading=$heading: ") {
            speed.horizontal shouldBe (25.722222 +- tolerance)
          }
        }
      }

    }

  }

  "DekunuPoint CSV codec" - {

    "should decode a row with all columns" in {
      val row =
        csvRow(
          "gpsTime"                   -> "1718454600",
          "gpsLatitude"               -> "45123456",
          "gpsLongitude"              -> "9123456",
          "gpsAltitudeMeters"         -> "3500.000",
          "gpsSpeedKnot"              -> "100.000",
          "gpsAngleDegree"            -> "90.00000",
          "instVertSpeedMetersPerSec" -> "-10.500",
          "gpsNumOfSats"              -> "12",
        )

      val decoded =
        DekunuPoint
          .csvRowDecoder(row)
          .toOption
          .get

      val expected =
        DekunuPoint(
          gpsTime = 1718454600L,
          gpsLatitude = 45123456.0,
          gpsLongitude = 9123456.0,
          gpsAltitudeMeters = 3500.0,
          gpsSpeedKnot = 100.0,
          gpsAngleDegree = 90.0,
          instVertSpeed = Some(-10.5),
          gpsNumOfSats = Some(12),
          extra = List.empty,
        )

      decoded shouldBe expected
    }

    "should decode a row without optional columns" in {
      val row =
        csvRow(
          "gpsTime"           -> "1718454600",
          "gpsLatitude"       -> "45123456",
          "gpsLongitude"      -> "9123456",
          "gpsAltitudeMeters" -> "3500.000",
          "gpsSpeedKnot"      -> "100.000",
          "gpsAngleDegree"    -> "90.00000",
        )

      val decoded =
        DekunuPoint
          .csvRowDecoder(row)
          .toOption
          .get

      decoded.instVertSpeed shouldBe None
      decoded.gpsNumOfSats shouldBe None
    }

    "should decode a negative Unix epoch time" in {
      val row =
        csvRow(
          "gpsTime"           -> "-86400",
          "gpsLatitude"       -> "45123456",
          "gpsLongitude"      -> "9123456",
          "gpsAltitudeMeters" -> "3500.000",
          "gpsSpeedKnot"      -> "100.000",
          "gpsAngleDegree"    -> "90.00000",
        )

      val actual =
        DekunuPoint
          .csvRowDecoder(row)
          .toOption
          .get
          .gpsTime

      actual shouldBe -86400L
    }

    "should encode and decode back to the same point" in {
      val point = dekunuPoint()

      val decoded =
        DekunuPoint
          .csvRowDecoder(DekunuPoint.csvRowEncoder(point))
          .toOption
          .get

      decoded shouldBe point
    }

    "should expose the Dekunu column names as required columns" in {
      val expected =
        Set(
          "gpsTime",
          "gpsLatitude",
          "gpsLongitude",
          "gpsAltitudeMeters",
          "gpsSpeedKnot",
          "gpsAngleDegree",
        )

      DekunuPoint.requiredColumns shouldBe expected
    }

  }

  "DekunuPoint sample file" - {

    val sampleFile = Paths.get("src/test/resources/dekunu_sample.zip")

    val points = TestFilesHandler.readCsv[DekunuPoint](sampleFile).streamResult()

    "should decode all data rows" in {
      points.size shouldBe TestFilesHandler.countLines(sampleFile)
    }

    "should decode the first row with the expected values" in {
      val expected =
        DekunuPoint(
          gpsTime = 1594380214L,
          gpsLatitude = 52559773.0,
          gpsLongitude = -390584.0,
          gpsAltitudeMeters = 58.0,
          gpsSpeedKnot = 78.45,
          gpsAngleDegree = 321.75,
          instVertSpeed = Some(3.6),
          gpsNumOfSats = Some(12),
          extra = List.empty,
        )

      points.head shouldBe expected
    }

    "should normalize the first row" in {
      val point = points.head

      dekunuReader.time(point) shouldBe Instant.ofEpochSecond(1594380214L)
      dekunuReader.altitude(point) shouldBe 58.0
      // 78.45 knots converted to m/s
      dekunuReader.speed(point).horizontal shouldBe (78.45 * 0.51444444 +- tolerance)
      dekunuReader.speed(point).vertical shouldBe 3.6
    }

    "should round-trip every row through the CSV codec" in {
      points.foreach { point =>
        val decoded =
          DekunuPoint
            .csvRowDecoder(DekunuPoint.csvRowEncoder(point))
            .toOption
            .get

        decoded shouldBe point
      }
    }

  }

  private def csvRow(values: (String, String)*): CsvRow[String] =
    CsvRow.fromNelHeaders(NonEmptyList.fromListUnsafe(values.toList))

  private def dekunuPoint(
    gpsTime: Long = 1718454600L,
    gpsAltitudeMeters: Double = 3500.0,
    gpsSpeedKnot: Double = 100.0,
    gpsAngleDegree: Double = 0.0,
    instVertSpeed: Option[Double] = Some(-10.5),
  ): DekunuPoint =
    DekunuPoint(
      gpsTime = gpsTime,
      gpsLatitude = 45123456.0,
      gpsLongitude = 9123456.0,
      gpsAltitudeMeters = gpsAltitudeMeters,
      gpsSpeedKnot = gpsSpeedKnot,
      gpsAngleDegree = gpsAngleDegree,
      instVertSpeed = instVertSpeed,
      gpsNumOfSats = Some(12),
      extra = List.empty,
    )

}
