package com.colofabrix.scala.beesight.formats.convert

import com.colofabrix.scala.beesight.formats.devices.*
import com.colofabrix.scala.beesight.{ IOValues, TestFilesHandler }
import fs2.data.csv.CsvRowDecoder
import java.nio.file.Paths
import java.time.{ Instant, OffsetDateTime }
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class FlySightConverterSpec extends AnyFreeSpec with Matchers with IOValues {

  "FlySightConverter" - {

    "keeps FlySight points unchanged" in {
      val source = FlysightPoint(
        time = OffsetDateTime.parse("2026-01-01T10:00:00Z"),
        lat = 51.0,
        lon = -1.0,
        hMSL = 1000.0,
        velN = 10.0,
        velE = 5.0,
        velD = 50.0,
        hAcc = 1.5,
        vAcc = 2.5,
        sAcc = 0.75,
        heading = 90.0,
        cAcc = 4.0,
        gpsFix = Some(3),
        numSV = 11,
      )

      val actual = source.toFlySight()

      actual shouldBe source
    }

    "maps Airlog points, rebuilding the velocity vector from speed and heading" in {
      val source = AirlogPoint(
        time = Instant.parse("2026-01-01T10:00:00Z"),
        coordinates = Coordinates(51.0, -1.0),
        altitude = Altitude(1000.0, 1005.0),
        motion = Motion(10.0, -5.0, 90.0, -5.1),
        orientation = None,
        acceleration = Acceleration(0.0, 0.0, 1.0),
        angularVelocity = AngularVelocity(0.0, 0.0, 0.0),
        metadata = Metadata(0, 7, 1),
        extra = List.empty,
      )

      val actual = source.toFlySight()

      actual.time.toInstant shouldBe source.time
      actual.lat shouldBe 51.0
      actual.lon shouldBe -1.0
      actual.hMSL shouldBe 1000.0
      actual.velN shouldBe 10.0 * Math.cos(Math.toRadians(90.0))
      actual.velE shouldBe 10.0 * Math.sin(Math.toRadians(90.0))
      actual.velD shouldBe -5.0
      actual.heading shouldBe 90.0
      actual.gpsFix shouldBe None
      actual.numSV shouldBe 7
      actual.hAcc shouldBe 0.0
      actual.vAcc shouldBe 0.0
      actual.sAcc shouldBe 0.0
      actual.cAcc shouldBe 0.0
    }

    "maps Dekunu points, converting coordinates from micro-degrees" in {
      val source = DekunuPoint(
        gpsTime = 1700000000L,
        gpsLatitude = 51000000.0,
        gpsLongitude = -1000000.0,
        gpsAltitudeMeters = 1000.0,
        gpsSpeedKnot = 10.0,
        gpsAngleDegree = 90.0,
        instVertSpeed = Some(50.0),
        gpsNumOfSats = Some(9),
        extra = List.empty,
      )

      val actual = source.toFlySight()

      actual.time.toInstant shouldBe Instant.ofEpochSecond(1700000000L)
      actual.lat shouldBe 51.0
      actual.lon shouldBe -1.0
      actual.hMSL shouldBe 1000.0
      actual.velN shouldBe 10.0 * 0.51444444 * Math.cos(Math.toRadians(90.0))
      actual.velE shouldBe 10.0 * 0.51444444 * Math.sin(Math.toRadians(90.0))
      actual.velD shouldBe 50.0
      actual.heading shouldBe 90.0
      actual.gpsFix shouldBe None
      actual.numSV shouldBe 9
    }

    "maps Generic points, deriving the heading from the velocity" in {
      val source = GenericPoint(
        time = OffsetDateTime.parse("2026-01-01T10:00:00Z"),
        hMSL = 1000.0,
        velN = 3.0,
        velE = 4.0,
        velD = -50.0,
        numSV = 8,
      )

      val actual = source.toFlySight()

      actual.lat shouldBe 0.0
      actual.lon shouldBe 0.0
      actual.heading shouldBe Math.toDegrees(Math.atan2(4.0, 3.0))
      actual.velN shouldBe 3.0
      actual.velE shouldBe 4.0
      actual.velD shouldBe -50.0
      actual.numSV shouldBe 8
    }

    "converts the FlySight sample without changing the points" in {
      val points = decodeSample[FlysightPoint]("flysight_sample.zip")

      val actual = points.map(_.toFlySight())

      actual should not be empty
      actual shouldBe points
    }

    "converts the Airlog sample with the expected first point" in {
      val points = decodeSample[AirlogPoint]("airlogone_sample.zip")

      val actual = points.map(_.toFlySight())

      actual should not be empty
      actual.head.lat shouldBe 53.498005
      actual.head.lon shouldBe -0.501443
      actual.head.hMSL shouldBe 3703.9
      actual.head.velD shouldBe 0.0
      actual.head.velN shouldBe 18.35 * Math.cos(Math.toRadians(84.9))
      actual.head.velE shouldBe 18.35 * Math.sin(Math.toRadians(84.9))
      actual.head.numSV shouldBe 0
    }

    "converts the Dekunu sample with degree coordinates" in {
      val points = decodeSample[DekunuPoint]("dekunu_sample.zip")

      val actual = points.map(_.toFlySight())

      actual should not be empty
      actual.head.time.toInstant shouldBe Instant.ofEpochSecond(1594380214L)
      actual.head.lat shouldBe 52.559773
      actual.head.lon shouldBe -0.390584
      actual.head.hMSL shouldBe 58.0
      actual.head.velD shouldBe 3.6
      actual.head.velN shouldBe 78.45 * 0.51444444 * Math.cos(Math.toRadians(321.75))
      actual.head.velE shouldBe 78.45 * 0.51444444 * Math.sin(Math.toRadians(321.75))
      actual.head.numSV shouldBe 12
    }

  }

  private def decodeSample[A](sample: String)(using CsvRowDecoder[A, String]): List[A] =
    TestFilesHandler
      .readCsv[A](Paths.get(s"src/test/resources/$sample"))
      .streamResult()

}
