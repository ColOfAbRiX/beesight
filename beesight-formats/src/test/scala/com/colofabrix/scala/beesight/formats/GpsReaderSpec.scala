package com.colofabrix.scala.beesight.formats

import com.colofabrix.scala.beesight.readers.GpsReader
import java.time.{ Instant, OffsetDateTime }
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import com.colofabrix.scala.beesight.formats.devices.FlysightPoint
import com.colofabrix.scala.beesight.formats.devices.DekunuPoint
import com.colofabrix.scala.beesight.formats.devices.{Acceleration, AirlogPoint, Altitude, AngularVelocity, Coordinates, Metadata, Motion}

class GpsReaderSpec extends AnyFreeSpec with Matchers {

  "GpsReader instances" - {

    "FlysightPoint carries all the accuracy fields" in {
      val point = FlysightPoint(
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
        extra = List.empty,
      )

      val reader = GpsReader[FlysightPoint]

      reader.horizontalAccuracy(point) shouldBe Some(1.5)
      reader.verticalAccuracy(point) shouldBe Some(2.5)
      reader.speedAccuracy(point) shouldBe Some(0.75)
      reader.courseAccuracy(point) shouldBe Some(4.0)
      reader.satellites(point) shouldBe Some(11)
    }

    "DekunuPoint has no accuracy data but reports the satellites" in {
      val point = DekunuPoint(
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

      val reader = GpsReader[DekunuPoint]

      reader.horizontalAccuracy(point) shouldBe None
      reader.verticalAccuracy(point) shouldBe None
      reader.speedAccuracy(point) shouldBe None
      reader.courseAccuracy(point) shouldBe None
      reader.satellites(point) shouldBe Some(9)
    }

    "AirlogPoint has no accuracy data but reports the satellites" in {
      val point = AirlogPoint(
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

      val reader = GpsReader[AirlogPoint]

      reader.horizontalAccuracy(point) shouldBe None
      reader.verticalAccuracy(point) shouldBe None
      reader.speedAccuracy(point) shouldBe None
      reader.courseAccuracy(point) shouldBe None
      reader.satellites(point) shouldBe Some(7)
    }

  }

}
