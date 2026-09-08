package com.colofabrix.scala.beesight.model.formats

import cats.data.NonEmptyList
import com.colofabrix.scala.beesight.{ IOValues, TestFilesHandler }
import com.colofabrix.scala.beesight.model.*
import fs2.data.csv.*
import java.nio.file.Paths
import java.time.*
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class AirlogPointAdapterSpec extends AnyFreeSpec with Matchers with IOValues {

  private val tolerance = 1e-10

  private val normalizer = AirlogPoint.airlogNormalizer

  "AirlogPoint PointReader" - {

    "field mapping" - {

      "should map time directly" in {
        val time  = Instant.parse("2024-06-15T12:30:00Z")
        val point = airlogPoint(time = time)
        normalizer.time(point) shouldBe time
      }

      "should map barometric altitude" in {
        val point = airlogPoint(alt = 4200.0)
        normalizer.altitude(point) shouldBe 4200.0
      }

      "should map sink rate to vertical speed" in {
        val point = airlogPoint(sink = -15.0)
        normalizer.speed(point).vertical shouldBe -15.0
      }

    }

    "heading decomposition into north and east" - {

      "heading 0° (north) should produce full north, zero east" in {
        val point = airlogPoint(speed = 100.0, heading = 0.0)
        val speed = normalizer.speed(point)
        speed.north shouldBe (100.0 +- tolerance)
        speed.east shouldBe (0.0 +- tolerance)
      }

      "heading 90° (east) should produce zero north, full east" in {
        val point = airlogPoint(speed = 100.0, heading = 90.0)
        val speed = normalizer.speed(point)
        speed.north shouldBe (0.0 +- tolerance)
        speed.east shouldBe (100.0 +- tolerance)
      }

      "heading 180° (south) should produce negative north, zero east" in {
        val point = airlogPoint(speed = 100.0, heading = 180.0)
        val speed = normalizer.speed(point)
        speed.north shouldBe (-100.0 +- tolerance)
        speed.east shouldBe (0.0 +- tolerance)
      }

      "heading 270° (west) should produce zero north, negative east" in {
        val point = airlogPoint(speed = 100.0, heading = 270.0)
        val speed = normalizer.speed(point)
        speed.north shouldBe (0.0 +- tolerance)
        speed.east shouldBe (-100.0 +- tolerance)
      }

      "heading 45° (northeast) should split speed equally between north and east" in {
        val point    = airlogPoint(speed = 100.0, heading = 45.0)
        val speed    = normalizer.speed(point)
        val expected = 100.0 * Math.cos(Math.toRadians(45.0))
        speed.north shouldBe (expected +- tolerance)
        speed.east shouldBe (expected +- tolerance)
      }

    }

    "zero speed" - {

      "should produce zero velocity regardless of heading" in {
        val point = airlogPoint(speed = 0.0, heading = 123.0)
        val speed = normalizer.speed(point)
        speed.north shouldBe (0.0 +- tolerance)
        speed.east shouldBe (0.0 +- tolerance)
      }

    }

    "magnitude preservation" - {

      "horizontal magnitude should equal input speed for any heading" in {
        val headings = List(0.0, 30.0, 45.0, 90.0, 135.0, 180.0, 225.0, 270.0, 315.0)
        headings.foreach { heading =>
          val point = airlogPoint(speed = 50.0, heading = heading)
          val speed = normalizer.speed(point)
          withClue(s"heading=$heading: ") {
            speed.horizontal shouldBe (50.0 +- tolerance)
          }
        }
      }

    }

  }

  "AirlogPoint CSV codec" - {

    "should decode an Airlog row" in {
      val row =
        csvRow(
          "time"    -> "2024-06-15T12:30:00Z",
          "lat"     -> "45.0000000",
          "lon"     -> "9.0000000",
          "alt"     -> "4200.0",
          "gpsAlt"  -> "4210.0",
          "speed"   -> "12.50",
          "sink"    -> "-15.00",
          "heading" -> "90.0",
          "Qw"      -> "1.0000",
          "Qx"      -> "0.0000",
          "Qy"      -> "0.0000",
          "Qz"      -> "0.0000",
          "Ax"      -> "0.000",
          "Ay"      -> "0.000",
          "Az"      -> "0.000",
          "Gx"      -> "0.000",
          "Gy"      -> "0.000",
          "Gz"      -> "0.000",
          "mark"    -> "0",
          "numSat"  -> "10",
          "msg"     -> "0",
          "b_sink"  -> "-14.00",
        )

      val decoded = AirlogPoint.csvRowDecoder(row).toOption.get

      decoded shouldBe AirlogPoint(
        time = Instant.parse("2024-06-15T12:30:00Z"),
        coordinates = Coordinates(45.0, 9.0),
        altitude = Altitude(4200.0, 4210.0),
        motion = Motion(12.5, -15.0, 90.0, -14.0),
        orientation = Some(Quaternion(1.0, 0.0, 0.0, 0.0)),
        acceleration = Acceleration(0.0, 0.0, 0.0),
        angularVelocity = AngularVelocity(0.0, 0.0, 0.0),
        metadata = Metadata(0, 10, 0),
        extra = List.empty,
      )
    }

    "should decode a row with an unparseable quaternion component to a None orientation" in {
      val row = csvRow(
        "time" -> "2024-06-15T12:30:00Z",
        "lat" -> "45.0000000",
        "lon" -> "9.0000000",
        "alt" -> "4200.0",
        "gpsAlt" -> "4210.0",
        "speed" -> "12.50",
        "sink" -> "-15.00",
        "heading" -> "90.0",
        "Qw" -> "1.0000",
        "Qx" -> "0.0000",
        "Qy" -> "-nan",
        "Qz" -> "0.0000",
        "Ax" -> "0.000",
        "Ay" -> "0.000",
        "Az" -> "0.000",
        "Gx" -> "0.000",
        "Gy" -> "0.000",
        "Gz" -> "0.000",
        "mark" -> "0",
        "numSat" -> "10",
        "msg" -> "0",
        "b_sink" -> "-14.00",
      )

      val decoded = AirlogPoint.csvRowDecoder(row).toOption.get

      decoded.orientation shouldBe None
      decoded.time shouldBe Instant.parse("2024-06-15T12:30:00Z")
      decoded.motion shouldBe Motion(12.5, -15.0, 90.0, -14.0)
    }

    "should decode a row with empty quaternion cells to a None orientation" in {
      val row = csvRow(
        "time" -> "2024-06-15T12:30:00Z",
        "lat" -> "45.0000000",
        "lon" -> "9.0000000",
        "alt" -> "4200.0",
        "gpsAlt" -> "4210.0",
        "speed" -> "12.50",
        "sink" -> "-15.00",
        "heading" -> "90.0",
        "Qw" -> "",
        "Qx" -> "",
        "Qy" -> "",
        "Qz" -> "",
        "Ax" -> "0.000",
        "Ay" -> "0.000",
        "Az" -> "0.000",
        "Gx" -> "0.000",
        "Gy" -> "0.000",
        "Gz" -> "0.000",
        "mark" -> "0",
        "numSat" -> "10",
        "msg" -> "0",
        "b_sink" -> "-14.00",
      )

      AirlogPoint.csvRowDecoder(row).toOption.get.orientation shouldBe None
    }

    "should encode a missing orientation as empty cells" in {
      val point  = airlogPoint(orientation = None)
      val encoded = AirlogPoint.csvRowEncoder(point)

      encoded("Qw") shouldBe Some("")
      encoded("Qx") shouldBe Some("")
      encoded("Qy") shouldBe Some("")
      encoded("Qz") shouldBe Some("")

      val decoded = AirlogPoint.csvRowDecoder(encoded).toOption.get

      decoded shouldBe point
    }

    "should encode and decode back to the same point" in {
      val point = airlogPoint()

      val decoded = AirlogPoint.csvRowDecoder(AirlogPoint.csvRowEncoder(point)).toOption.get

      decoded shouldBe point
    }

    "should expose the Airlog column names as required columns" in {
      val expected =
        Set(
          "time",
          "lat",
          "lon",
          "alt",
          "gpsAlt",
          "speed",
          "sink",
          "heading",
          "Qw",
          "Qx",
          "Qy",
          "Qz",
          "Ax",
          "Ay",
          "Az",
          "Gx",
          "Gy",
          "Gz",
          "mark",
          "numSat",
          "msg",
          "b_sink",
        )

      AirlogPoint.requiredColumns shouldBe expected
    }

  }

  "AirlogPoint sample file" - {

    val sampleFile = Paths.get("src/test/resources/airlogone_sample.zip")

    val points = TestFilesHandler.readCsv[AirlogPoint](sampleFile).streamResult()

    "should decode all data rows" in {
      // All 2106 data rows decode, including the two holding an undecodable '-nan' quaternion value
      points.size shouldBe 2106
    }

    "should decode the first row with the expected values" in {
      val expected =
        AirlogPoint(
          time = Instant.parse("2026-08-23T08:11:35Z"),
          coordinates = Coordinates(53.498005, -0.501443),
          altitude = Altitude(3703.9, -9.8),
          motion = Motion(18.35, 0.0, 84.9, -6.59),
          orientation = Some(Quaternion(0.3135, -0.9155, -0.0959, 0.233)),
          acceleration = Acceleration(-0.355, -0.609, -0.737),
          angularVelocity = AngularVelocity(6.79, -94.92, 30.03),
          metadata = Metadata(0, 0, 1),
          extra = List.empty,
        )

      points.head shouldBe expected
    }

    "should normalize the first row" in {
      val point = points.head

      normalizer.time(point) shouldBe Instant.parse("2026-08-23T08:11:35Z")
      normalizer.altitude(point) shouldBe 3703.9
      normalizer.speed(point).horizontal shouldBe (18.35 +- tolerance)
      normalizer.speed(point).vertical shouldBe 0.0
    }

    "should round-trip every row through the CSV codec" in {
      points.foreach { point =>
        val decoded = AirlogPoint.csvRowDecoder(AirlogPoint.csvRowEncoder(point)).toOption.get
        decoded shouldBe point
      }
    }

  }

  private def csvRow(values: (String, String)*): CsvRow[String] =
    CsvRow.fromNelHeaders(NonEmptyList.fromListUnsafe(values.toList))

  private def airlogPoint(
    time: Instant = Instant.parse("2024-01-01T00:00:00Z"),
    alt: Double = 1000.0,
    speed: Double = 0.0,
    sink: Double = 0.0,
    heading: Double = 0.0,
    orientation: Option[Quaternion] = Some(Quaternion(1.0, 0.0, 0.0, 0.0)),
  ): AirlogPoint =
    AirlogPoint(
      time = time,
      coordinates = Coordinates(0.0, 0.0),
      altitude = Altitude(alt, alt),
      motion = Motion(speed, sink, heading, 0.0),
      orientation = orientation,
      acceleration = Acceleration(0.0, 0.0, 0.0),
      angularVelocity = AngularVelocity(0.0, 0.0, 0.0),
      metadata = Metadata(0, 10, 0),
      extra = List.empty,
    )

}
