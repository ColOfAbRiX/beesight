package com.colofabrix.scala.beesight.formats.devices

import cats.data.NonEmptyList
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.formats.devices.FlysightPoint
import com.colofabrix.scala.beesight.{ IOValues, TestFilesHandler }
import fs2.data.csv.*
import java.nio.file.Paths
import java.time.*
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class FlysightPointAdapterSpec extends AnyFreeSpec with Matchers with IOValues {

  private val flysightReader = FlysightPoint.flysightPointReader

  "FlysightPoint PointReader" - {

    "field mapping" - {

      "should map time to Instant" in {
        val time  = OffsetDateTime.of(2024, 6, 15, 12, 30, 0, 0, ZoneOffset.UTC)
        val point = flysightPoint(time = time)
        flysightReader.time(point) shouldBe time.toInstant
      }

      "should map hMSL to altitude" in {
        val point = flysightPoint(hMSL = 3500.0)
        flysightReader.altitude(point) shouldBe 3500.0
      }

      "should map velN to speed.north" in {
        val point = flysightPoint(velN = 12.5)
        flysightReader.speed(point).north shouldBe 12.5
      }

      "should map velE to speed.east" in {
        val point = flysightPoint(velE = -8.3)
        flysightReader.speed(point).east shouldBe -8.3
      }

      "should map velD to speed.vertical" in {
        val point = flysightPoint(velD = 55.0)
        flysightReader.speed(point).vertical shouldBe 55.0
      }

    }

    "velocity direction" - {

      "should preserve positive velocities" in {
        val point = flysightPoint(velN = 10.0, velE = 20.0, velD = 30.0)
        flysightReader.speed(point) shouldBe GeoVector(10.0, 20.0, 30.0)
      }

      "should preserve negative velocities" in {
        val point = flysightPoint(velN = -10.0, velE = -20.0, velD = -5.0)
        flysightReader.speed(point) shouldBe GeoVector(-10.0, -20.0, -5.0)
      }

      "should preserve zero velocities" in {
        val point = flysightPoint(velN = 0.0, velE = 0.0, velD = 0.0)
        flysightReader.speed(point) shouldBe GeoVector.zero
      }

    }

    "timezone handling" - {

      "should correctly convert non-UTC timezone to Instant" in {
        val time  = OffsetDateTime.of(2024, 6, 15, 14, 30, 0, 0, ZoneOffset.ofHours(2))
        val point = flysightPoint(time = time)
        flysightReader.time(point) shouldBe Instant.parse("2024-06-15T12:30:00Z")
      }

    }

  }

  "FlysightPoint CSV codec" - {

    "should decode a FlySight row" in {
      val row =
        csvRow(
          "time"    -> "2024-06-15T12:30:00Z",
          "lat"     -> "45.0000000",
          "lon"     -> "9.0000000",
          "hMSL"    -> "3500.000",
          "velN"    -> "12.50",
          "velE"    -> "-8.30",
          "velD"    -> "55.00",
          "hAcc"    -> "1.000",
          "vAcc"    -> "1.000",
          "sAcc"    -> "1.00",
          "heading" -> "90.00000",
          "cAcc"    -> "5.00000",
          "gpsFix"  -> "3",
          "numSV"   -> "10",
        )

      val expected =
        FlysightPoint(
          time = OffsetDateTime.of(2024, 6, 15, 12, 30, 0, 0, ZoneOffset.UTC),
          lat = 45.0,
          lon = 9.0,
          hMSL = 3500.0,
          velN = 12.5,
          velE = -8.3,
          velD = 55.0,
          hAcc = 1.0,
          vAcc = 1.0,
          sAcc = 1.0,
          heading = 90.0,
          cAcc = 5.0,
          gpsFix = Some(3),
          numSV = 10,
        )

      val decoded =
        FlysightPoint
          .csvRowDecoder(row)
          .toOption
          .get

      decoded shouldBe expected
    }

    "should decode a whole-second timestamp without a fractional part" in {
      val row =
        csvRow(
          "time"    -> "2026-05-27T15:39:39Z",
          "lat"     -> "45.0000000",
          "lon"     -> "9.0000000",
          "hMSL"    -> "3500.000",
          "velN"    -> "12.50",
          "velE"    -> "-8.30",
          "velD"    -> "55.00",
          "hAcc"    -> "1.000",
          "vAcc"    -> "1.000",
          "sAcc"    -> "1.00",
          "heading" -> "90.00000",
          "cAcc"    -> "5.00000",
          "gpsFix"  -> "3",
          "numSV"   -> "10",
        )

      val actual =
        FlysightPoint
          .csvRowDecoder(row)
          .toOption
          .get
          .time
          .toInstant

      actual shouldBe Instant.parse("2026-05-27T15:39:39Z")
    }

    "should decode a row without the optional gpsFix column" in {
      val row =
        csvRow(
          "time"    -> "2024-06-15T12:30:00Z",
          "lat"     -> "45.0000000",
          "lon"     -> "9.0000000",
          "hMSL"    -> "3500.000",
          "velN"    -> "12.50",
          "velE"    -> "-8.30",
          "velD"    -> "55.00",
          "hAcc"    -> "1.000",
          "vAcc"    -> "1.000",
          "sAcc"    -> "1.00",
          "heading" -> "90.00000",
          "cAcc"    -> "5.00000",
          "numSV"   -> "10",
        )

      val decoded =
        FlysightPoint
          .csvRowDecoder(row)
          .toOption
          .get

      decoded.gpsFix shouldBe None
    }

    "should encode and decode back to the same point" in {
      val point = flysightPoint()

      val decoded =
        FlysightPoint
          .csvRowDecoder(FlysightPoint.csvRowEncoder(point))
          .toOption
          .get

      decoded shouldBe point
    }

    "should expose the FlySight column names as required columns" in {
      FlysightPoint.requiredColumns shouldBe Set(
        "time",
        "lat",
        "lon",
        "hMSL",
        "velN",
        "velE",
        "velD",
        "hAcc",
        "vAcc",
        "sAcc",
        "heading",
        "cAcc",
        "numSV",
      )
    }

  }

  "FlysightPoint sample file" - {

    val sampleFile = Paths.get("src/test/resources/flysight_sample.zip")

    val points = TestFilesHandler.readCsv[FlysightPoint](sampleFile).streamResult()

    "should decode all data rows" in {
      points.size shouldBe TestFilesHandler.countLines(sampleFile) - 1
    }

    "should decode the first row with the expected values" in {
      val expected =
        FlysightPoint(
          time = OffsetDateTime.parse("2026-05-08T09:57:08.200Z"),
          lat = 52.9981951,
          lon = -0.8137493,
          hMSL = 2202.351,
          velN = 41.19,
          velE = -47.45,
          velD = -5.99,
          hAcc = 70.733,
          vAcc = 117.188,
          sAcc = 2.53,
          heading = 310.96308,
          cAcc = 2.03103,
          gpsFix = Some(3),
          numSV = 4,
        )

      points.head shouldBe expected
    }

    "should normalize the first row" in {
      val point = points.head

      flysightReader.time(point) shouldBe Instant.parse("2026-05-08T09:57:08.200Z")
      flysightReader.altitude(point) shouldBe 2202.351
      flysightReader.speed(point) shouldBe GeoVector(41.19, -47.45, -5.99)
    }

    "should round-trip every row through the CSV codec" in {
      points.foreach { point =>
        val decoded =
          FlysightPoint
            .csvRowDecoder(FlysightPoint.csvRowEncoder(point))
            .toOption
            .get

        decoded shouldBe point
      }
    }

  }

  private def csvRow(values: (String, String)*): CsvRow[String] =
    CsvRow.fromNelHeaders(NonEmptyList.fromListUnsafe(values.toList))

  private def flysightPoint(
    time: OffsetDateTime = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC),
    hMSL: Double = 1000.0,
    velN: Double = 0.0,
    velE: Double = 0.0,
    velD: Double = 0.0,
  ): FlysightPoint =
    FlysightPoint(
      time = time,
      lat = 0.0,
      lon = 0.0,
      hMSL = hMSL,
      velN = velN,
      velE = velE,
      velD = velD,
      hAcc = 0.0,
      vAcc = 0.0,
      sAcc = 0.0,
      heading = 0.0,
      cAcc = 0.0,
      gpsFix = Some(3),
      numSV = 10,
    )

}
