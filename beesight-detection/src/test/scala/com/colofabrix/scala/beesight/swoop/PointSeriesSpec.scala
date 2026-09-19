package com.colofabrix.scala.beesight.swoop

import com.colofabrix.scala.beesight.model.{ GeoVector, IndexedPoint }
import com.colofabrix.scala.beesight.swoop.model.PointSeries
import java.time.Instant
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class PointSeriesSpec extends AnyFreeSpec with Matchers {

  //  Helpers  //

  private def point(
    headingDeg: Double,
    hSpeed: Double,
    vSpeed: Double,
    alt: Double,
    tMillis: Long
  ): IndexedPoint = {
    val rad = Math.toRadians(headingDeg)
    IndexedPoint(
      idx = tMillis,
      time = Instant.ofEpochMilli(tMillis),
      altitude = alt,
      speed = GeoVector(
        north = hSpeed * Math.cos(rad),
        east = hSpeed * Math.sin(rad),
        vertical = vSpeed,
      )
    )
  }

  private def simpleSeries: PointSeries = PointSeries(Vector(
    point(headingDeg = 0.0, hSpeed = 10.0, vSpeed = 5.0, alt = 1000.0, tMillis = 0L),
    point(headingDeg = 45.0, hSpeed = 15.0, vSpeed = 3.0, alt = 900.0, tMillis = 1000L),
    point(headingDeg = 90.0, hSpeed = 20.0, vSpeed = 1.0, alt = 800.0, tMillis = 2000L),
  ))

  //  Tests  //

  "PointSeries" - {

    "length returns the number of points" in {
      simpleSeries.length shouldBe 3
      PointSeries(Vector.empty).length shouldBe 0
    }

    "apply returns the point at the given index" in {
      val pts = simpleSeries
      pts(0).altitude shouldBe 1000.0
      pts(1).altitude shouldBe 900.0
      pts(2).altitude shouldBe 800.0
    }

    "verticalSpeed returns the vertical component" in {
      val pts = simpleSeries
      pts.verticalSpeed(0) shouldBe 5.0 +- 1e-9
      pts.verticalSpeed(1) shouldBe 3.0 +- 1e-9
      pts.verticalSpeed(2) shouldBe 1.0 +- 1e-9
    }

    "horizontalSpeed returns the magnitude of north/east" in {
      // heading 0: north=10, east=0 → 10; heading 45: north=10.607, east=10.607 → 15; heading 90: north=0, east=20 → 20
      val pts = simpleSeries
      pts.horizontalSpeed(0) shouldBe 10.0 +- 1e-9
      pts.horizontalSpeed(1) shouldBe 15.0 +- 1e-9
      pts.horizontalSpeed(2) shouldBe 20.0 +- 1e-9
    }

    "totalSpeed returns the 3D magnitude" in {
      val pts = simpleSeries
      pts.totalSpeed(0) shouldBe Math.sqrt(100 + 25) +- 1e-9 // sqrt(10^2 + 5^2)
      pts.totalSpeed(1) shouldBe Math.sqrt(225 + 9) +- 1e-9  // sqrt(15^2 + 3^2)
      pts.totalSpeed(2) shouldBe Math.sqrt(400 + 1) +- 1e-9  // sqrt(20^2 + 1^2)
    }

    "northSpeed returns the north component" in {
      // heading 0 → 10*cos(0)=10, heading 45 → 15*cos(45)=10.607, heading 90 → 20*cos(90)=0
      val pts = simpleSeries
      pts.northSpeed(0) shouldBe 10.0 +- 1e-9
      pts.northSpeed(1) shouldBe 15.0 * Math.cos(Math.toRadians(45)) +- 1e-9
      pts.northSpeed(2) shouldBe 0.0 +- 1e-9
    }

    "eastSpeed returns the east component" in {
      // heading 0 → 10*sin(0)=0, heading 45 → 15*sin(45)=10.607, heading 90 → 20*sin(90)=20
      val pts = simpleSeries
      pts.eastSpeed(0) shouldBe 0.0 +- 1e-9
      pts.eastSpeed(1) shouldBe 15.0 * Math.sin(Math.toRadians(45)) +- 1e-9
      pts.eastSpeed(2) shouldBe 20.0 +- 1e-9
    }

    "altitude returns the altitude" in {
      val pts = simpleSeries
      pts.altitude(0) shouldBe 1000.0 +- 1e-9
      pts.altitude(1) shouldBe 900.0 +- 1e-9
      pts.altitude(2) shouldBe 800.0 +- 1e-9
    }

    "heading returns the heading in degrees [0, 360)" in {
      val pts = simpleSeries
      pts.heading(0) shouldBe 0.0 +- 1e-9
      pts.heading(1) shouldBe 45.0 +- 1e-9
      pts.heading(2) shouldBe 90.0 +- 1e-9
    }

    "time returns the Instant" in {
      val pts = simpleSeries
      pts.time(0).toEpochMilli shouldBe 0L
      pts.time(1).toEpochMilli shouldBe 1000L
      pts.time(2).toEpochMilli shouldBe 2000L
    }

    "timeMillis returns epoch millis as Double" in {
      val pts = simpleSeries
      pts.timeMillis(0) shouldBe 0.0 +- 1e-9
      pts.timeMillis(1) shouldBe 1000.0 +- 1e-9
      pts.timeMillis(2) shouldBe 2000.0 +- 1e-9
    }

  }

}
