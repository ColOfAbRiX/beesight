package com.colofabrix.scala.beesight.swoop

import com.colofabrix.scala.beesight.model.{ EventPoint, GeoVector, IndexedPoint }
import com.colofabrix.scala.beesight.swoop.computations.SwoopGround
import com.colofabrix.scala.beesight.swoop.model.PointSeries
import java.time.Instant
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class SwoopGroundSpec extends AnyFreeSpec with Matchers {

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

  private def groundSeries: PointSeries = PointSeries(Vector(
    point(0.0, 10.0, 5.0, 1000.0, 0L),
    point(0.0, 10.0, 5.0, 900.0, 1000L),
    point(0.0, 10.0, 5.0, 800.0, 2000L),
    point(0.0, 10.0, 5.0, 700.0, 3000L),
    point(0.0, 10.0, 5.0, 600.0, 4000L),
    point(0.0, 10.0, 5.0, 500.0, 5000L),
    point(0.0, 10.0, 5.0, 400.0, 6000L),
    point(0.0, 10.0, 5.0, 300.0, 7000L),
    point(0.0, 10.0, 5.0, 200.0, 8000L),
    point(0.0, 10.0, 5.0, 100.0, 9000L),
    point(0.0, 10.0, 5.0, 100.0, 10000L),
    point(0.0, 10.0, 5.0, 100.0, 11000L),
    point(0.0, 10.0, 5.0, 100.0, 12000L),
  ))

  //  Tests  //

  "SwoopGround.detect" - {

    "uses the median altitude from the landing point window when landing is provided" in {
      val series  = groundSeries
      val landing = EventPoint(index = 10L, altitude = 100.0, Instant.EPOCH, GeoVector.zero)

      val ground = SwoopGround.detect(series, Some(landing))

      // The 10-second window starting at index 10 covers indices 10-12 (all alt=100)
      ground shouldBe 100.0 +- 1e-9
    }

    "falls back to the last slow point when no landing event is provided" in {
      val series = groundSeries

      val ground = SwoopGround.detect(series, None)

      // All points have horizontal speed 10 > groundSlowSpeed (4.4704), so no slow point is found.
      // Falls back to last altitude = 100.
      ground shouldBe 100.0 +- 1e-9
    }

    "falls back to the last altitude when no landing and no slow point" in {
      val series = PointSeries(Vector(
        point(0.0, 20.0, 5.0, 1000.0, 0L),
        point(0.0, 20.0, 5.0, 900.0, 1000L),
      ))

      val ground = SwoopGround.detect(series, None)

      ground shouldBe 900.0 +- 1e-9
    }

  }

}
