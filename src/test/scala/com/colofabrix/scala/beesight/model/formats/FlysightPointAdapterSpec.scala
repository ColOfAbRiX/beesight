package com.colofabrix.scala.beesight.model.formats

import com.colofabrix.scala.beesight.model.GeoVector
import java.time.*
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class FlysightPointAdapterSpec extends AnyFreeSpec with Matchers {

  "FlysightPoint FileFormatAdapter" - {

    "toDataPoint field mapping" - {

      "should map time to Instant" in {
        val time = OffsetDateTime.of(2024, 6, 15, 12, 30, 0, 0, ZoneOffset.UTC)
        val point = flysightPoint(time = time)
        val result = FlysightPoint.flysightFormatAdapter.toDataPoint(point)
        result.time shouldBe time.toInstant
      }

      "should map hMSL to altitude" in {
        val point = flysightPoint(hMSL = 3500.0)
        val result = FlysightPoint.flysightFormatAdapter.toDataPoint(point)
        result.altitude shouldBe 3500.0
      }

      "should map velN to speed.north" in {
        val point = flysightPoint(velN = 12.5)
        val result = FlysightPoint.flysightFormatAdapter.toDataPoint(point)
        result.speed.north shouldBe 12.5
      }

      "should map velE to speed.east" in {
        val point = flysightPoint(velE = -8.3)
        val result = FlysightPoint.flysightFormatAdapter.toDataPoint(point)
        result.speed.east shouldBe -8.3
      }

      "should map velD to speed.vertical" in {
        val point = flysightPoint(velD = 55.0)
        val result = FlysightPoint.flysightFormatAdapter.toDataPoint(point)
        result.speed.vertical shouldBe 55.0
      }

    }

    "velocity direction" - {

      "should preserve positive velocities" in {
        val point = flysightPoint(velN = 10.0, velE = 20.0, velD = 30.0)
        val result = FlysightPoint.flysightFormatAdapter.toDataPoint(point)
        result.speed shouldBe GeoVector(10.0, 20.0, 30.0)
      }

      "should preserve negative velocities" in {
        val point = flysightPoint(velN = -10.0, velE = -20.0, velD = -5.0)
        val result = FlysightPoint.flysightFormatAdapter.toDataPoint(point)
        result.speed shouldBe GeoVector(-10.0, -20.0, -5.0)
      }

      "should preserve zero velocities" in {
        val point = flysightPoint(velN = 0.0, velE = 0.0, velD = 0.0)
        val result = FlysightPoint.flysightFormatAdapter.toDataPoint(point)
        result.speed shouldBe GeoVector.zero
      }

    }

    "timezone handling" - {

      "should correctly convert non-UTC timezone to Instant" in {
        val time = OffsetDateTime.of(2024, 6, 15, 14, 30, 0, 0, ZoneOffset.ofHours(2))
        val point = flysightPoint(time = time)
        val result = FlysightPoint.flysightFormatAdapter.toDataPoint(point)
        result.time shouldBe Instant.parse("2024-06-15T12:30:00Z")
      }

    }

  }

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
      gpsFix = 3,
      numSV = 10,
      extra = List.empty,
    )

}
