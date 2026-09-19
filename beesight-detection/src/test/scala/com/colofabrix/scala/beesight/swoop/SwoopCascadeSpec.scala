package com.colofabrix.scala.beesight.swoop

import com.colofabrix.scala.beesight.model.{ GeoVector, IndexedPoint }
import com.colofabrix.scala.beesight.swoop.model.PointSeries
import java.time.Instant
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class SwoopCascadeSpec extends AnyFreeSpec with Matchers {

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

  //  findPatternEntry  //

  "SwoopCascade.findPatternEntry" - {

    "finds the first crossing below the pattern entry altitude above ground" in {
      //  ground=0: patternEntryAltitude=762. AGL=alt-ground=alt.
      //  alt: 800 → 750 crosses below 762 at index 1.
      val series = PointSeries(Vector(
        point(0.0, 10.0, 5.0, 800.0, 0L),
        point(0.0, 10.0, 5.0, 750.0, 1000L),
        point(0.0, 10.0, 5.0, 700.0, 2000L),
      ))

      SwoopCascade.findPatternEntry(series, ground = 0.0) shouldBe Some(1)
    }

    "returns None when never crosses below" in {
      val series = PointSeries(Vector(
        point(0.0, 10.0, 5.0, 800.0, 0L),
        point(0.0, 10.0, 5.0, 770.0, 1000L),
        point(0.0, 10.0, 5.0, 780.0, 2000L),
      ))

      SwoopCascade.findPatternEntry(series, ground = 0.0) shouldBe None
    }

    "accounts for ground offset" in {
      //  ground=100: AGL = alt-100. Need crossing below 762 → alt < 862.
      val series = PointSeries(Vector(
        point(0.0, 10.0, 5.0, 900.0, 0L),    // AGL=800
        point(0.0, 10.0, 5.0, 850.0, 1000L), // AGL=750 < 762 → crossing at index 1
      ))

      SwoopCascade.findPatternEntry(series, ground = 100.0) shouldBe Some(1)
    }

  }

  //  findDivePoint  //

  "SwoopCascade.findDivePoint" - {

    "finds the first point achieving the maximum descent rate below the altitude cap" in {
      //  ground=0: diveAltitudeCap=304.8. AGL=alt.
      //  alt: 300 → 250 → 200 all below 304.8. vSpeed: 5 → 30 → 10. Max at index 1 (30 m/s).
      val series = PointSeries(Vector(
        point(0.0, 10.0, 5.0, 300.0, 0L),
        point(0.0, 10.0, 30.0, 250.0, 1000L),
        point(0.0, 10.0, 10.0, 200.0, 2000L),
      ))

      SwoopCascade.findDivePoint(series, patternEntry = 0, ground = 0.0) shouldBe Some(1)
    }

    "returns None when all points are above the altitude cap" in {
      val series = PointSeries(Vector(
        point(0.0, 10.0, 5.0, 500.0, 0L),     // AGL=500 > 304.8
        point(0.0, 10.0, 30.0, 400.0, 1000L), // AGL=400 > 304.8
      ))

      SwoopCascade.findDivePoint(series, patternEntry = 0, ground = 0.0) shouldBe None
    }

    "returns None when no point has positive descent" in {
      val series = PointSeries(Vector(
        point(0.0, 10.0, 0.0, 200.0, 0L),
        point(0.0, 10.0, 0.0, 150.0, 1000L),
      ))

      SwoopCascade.findDivePoint(series, patternEntry = 0, ground = 0.0) shouldBe None
    }

    "only considers points after patternEntry" in {
      //  patternEntry=2: only indices 2,3 are considered.
      val series = PointSeries(Vector(
        point(0.0, 10.0, 50.0, 200.0, 0L),    // index 0: max descent, but before patternEntry
        point(0.0, 10.0, 50.0, 180.0, 1000L), // index 1: before patternEntry
        point(0.0, 10.0, 10.0, 250.0, 2000L), // index 2: patternEntry
        point(0.0, 10.0, 20.0, 200.0, 3000L), // index 3: max descent after patternEntry
      ))

      SwoopCascade.findDivePoint(series, patternEntry = 2, ground = 0.0) shouldBe Some(3)
    }

  }

  //  findStop  //

  "SwoopCascade.findStop" - {

    "finds the stop point after the heading fix" in {
      //  heading fix: first point where verticalSpeed < 4.4704 AND horizontalSpeed < 8.9408
      //  Then stop: first point where horizontalSpeed < 0.89408 OR deviating > 45° from swoop heading.
      val series = PointSeries(Vector(
        point(0.0, 20.0, 30.0, 200.0, 0L),    // fast
        point(0.0, 20.0, 30.0, 150.0, 1000L), // fast
        point(0.0, 5.0, 2.0, 100.0, 2000L),   // heading fix: v=2 < 4.47, h=5 < 8.94 → swoopHeading=0°
        point(0.0, 5.0, 2.0, 90.0, 3000L),    // still slow, h=5 > 0.89
        point(0.0, 0.5, 2.0, 80.0, 4000L),    // h=0.5 < 0.89 → stop
      ))

      SwoopCascade.findStop(series, diveIdx = 0) shouldBe Some(4)
    }

    "returns None when heading fix never occurs" in {
      val series = PointSeries(Vector(
        point(0.0, 20.0, 30.0, 200.0, 0L),
        point(0.0, 20.0, 30.0, 150.0, 1000L),
      ))

      SwoopCascade.findStop(series, diveIdx = 0) shouldBe None
    }

    "detects stop when heading deviates from swoop heading" in {
      val series = PointSeries(Vector(
        point(0.0, 20.0, 30.0, 200.0, 0L),
        point(0.0, 5.0, 2.0, 100.0, 2000L), // heading fix: swoopHeading=0°
        point(0.0, 5.0, 2.0, 90.0, 3000L),
        point(100.0, 5.0, 2.0, 80.0, 4000L), // heading deviates 100° > 45° → stop
      ))

      SwoopCascade.findStop(series, diveIdx = 0) shouldBe Some(3)
    }

  }

  //  findRolloutStart  //

  "SwoopCascade.findRolloutStart" - {

    "finds the maximum descent rate point within the rollout window above stop altitude" in {
      //  rolloutWindow=30.48. Walk forward from topOfTurn while alt-stop.alt >= 30.48.
      //  Then walk backward while descent strictly increases.
      val series = PointSeries(Vector(
        point(0.0, 10.0, 5.0, 300.0, 0L), // topOfTurn=0
        point(0.0, 10.0, 15.0, 250.0, 1000L),
        point(0.0, 10.0, 25.0, 200.0, 2000L),
        point(0.0, 10.0, 20.0, 150.0, 3000L),
        point(0.0, 10.0, 10.0, 100.0, 4000L),
        point(0.0, 10.0, 5.0, 50.0, 5000L),
        point(0.0, 10.0, 2.0, 30.0, 6000L), // stop
      ))

      SwoopCascade.findRolloutStart(series, topOfTurn = 0, stopIdx = 6, rolloutWindow = 30.48) shouldBe Some(2)
    }

    "returns None when walk reaches topOfTurn without finding rollout" in {
      //  All points are within the rollout window, so startIdx walks all the way to stopIdx.
      //  Walking backward from stopIdx: vz decreases going backward (2, 10, 20, 25, 15, 5) → never ascending.
      //  Actually: from startIdx walking backward: vz increases → ascending → keep walking.
      //  When prev < topOfTurn → failed.
      val series = PointSeries(Vector(
        point(0.0, 10.0, 10.0, 100.0, 0L), // topOfTurn=0, within rollout window
        point(0.0, 10.0, 20.0, 80.0, 1000L),
        point(0.0, 10.0, 5.0, 60.0, 2000L),
      ))

      SwoopCascade.findRolloutStart(series, topOfTurn = 0, stopIdx = 2, rolloutWindow = 1000.0) shouldBe None
    }

  }

  //  findBottomOfTurn  //

  "SwoopCascade.findBottomOfTurn" - {

    "finds the bottom of turn using the altitude crossing" in {
      //  bottomCandidateWalk: walk forward while altitude decreases or descent is fast.
      //  bottomVzCross=1.25. bottomAltitudeOffset=1.0.
      //  Bottom altitude = lowest altitude; targetAltitude = bottom + 1.0.
      val series = PointSeries(Vector(
        point(0.0, 10.0, 2.0, 100.0, 0L), // rollout
        point(0.0, 10.0, 2.0, 95.0, 1000L),
        point(0.0, 10.0, 2.0, 90.0, 2000L),
        point(0.0, 10.0, 2.0, 85.0, 3000L),
        point(0.0, 10.0, 2.0, 80.0, 4000L), // bottom candidate
        point(0.0, 10.0, 2.0, 82.0, 5000L), // ascending → stop candidate walk
        point(0.0, 10.0, 2.0, 85.0, 6000L), // stop
      ))

      val result = SwoopCascade.findBottomOfTurn(series, rolloutIdx = 0, stopIdx = 6)
      result should be >= 0
    }

    "returns rolloutIdx + 1 when no crossing is found" in {
      val series = PointSeries(Vector(
        point(0.0, 10.0, 2.0, 100.0, 0L),
        point(0.0, 10.0, 2.0, 100.0, 1000L),
        point(0.0, 10.0, 2.0, 100.0, 2000L),
      ))

      val result = SwoopCascade.findBottomOfTurn(series, rolloutIdx = 0, stopIdx = 2)
      result shouldBe 1
    }

  }

}
