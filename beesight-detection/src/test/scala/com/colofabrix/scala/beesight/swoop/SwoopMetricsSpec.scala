package com.colofabrix.scala.beesight.swoop

import com.colofabrix.scala.beesight.model.{ DataPoint, GeoVector, IndexedPoint }
import com.colofabrix.scala.beesight.swoop.model.PointSeries
import java.time.Instant
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

/**
 * Unit tests for [[SwoopMetrics]].
 *
 * The turn scenarios are built from synthetic heading sequences at a constant speed; the expected values are
 * hand-computed from the documented behaviour of the unwrap pass (see [[SwoopMetrics.turnAnglesWithUnwrap]] and the
 * thresholds in [[SwoopConstants]]).
 */
class SwoopMetricsSpec extends AnyFreeSpec with Matchers {

  //  Helpers  //

  /** A series of points one second apart, heading north, with the given per-index vertical and horizontal speeds. */
  private def speedSeries(vertical: Vector[Double], horizontal: Vector[Double]): PointSeries =
    seriesOf(
      headings = Vector.fill(vertical.length)(0.0),
      horizontal = horizontal,
      vertical = vertical,
      dtMillis = 1000.0
    )

  /** A series of points at 100 ms spacing flying the given headings at a constant speed. */
  private def turnSeries(
    headings: Vector[Double],
    vertical: Double,
    horizontal: Double = 10.0,
    dtMillis: Double = 100.0
  ): PointSeries =
    seriesOf(
      headings = headings,
      horizontal = Vector.fill(headings.length)(horizontal),
      vertical = Vector.fill(headings.length)(vertical),
      dtMillis = dtMillis
    )

  private def seriesOf(
    headings: Vector[Double],
    horizontal: Vector[Double],
    vertical: Vector[Double],
    dtMillis: Double
  ): PointSeries =
    PointSeries(
      headings
        .indices
        .map { i =>
          val rad = Math.toRadians(headings(i))
          IndexedPoint(
            i.toLong,
            DataPoint(
              Instant.ofEpochMilli((i * dtMillis).toLong),
              1000.0 - i,
              GeoVector(
                north = horizontal(i) * Math.cos(rad),
                east = horizontal(i) * Math.sin(rad),
                vertical = vertical(i),
              )
            )
          )
        }
        .toVector
    )

  //  maxSpeeds  //

  "SwoopMetrics.maxSpeeds" - {

    "selects the maximum speed within each detection window" in {
      //  Vertical peaks at index 7, total at index 5 (sqrt(10^2 + 7^2)), horizontal at index 4; the large speeds at
      //  the stop index must be excluded by the half-open windows.
      val series = speedSeries(
        vertical = Vector(1, 1, 2, 3, 1, 7, 1, 8, 100, 1),
        horizontal = Vector(1, 1, 5, 5, 12, 10, 11, 5, 100, 1)
      )

      val result = SwoopMetrics.maxSpeeds(series, diveIdx = 1, topOfTurn = 2, rolloutIdx = 4, stopIdx = 8)

      result.vertical.speed shouldBe 8.0 +- 1e-9
      result.vertical.index shouldBe 7
      result.total.speed shouldBe Math.sqrt(149.0) +- 1e-9
      result.total.index shouldBe 5
      result.horizontal.speed shouldBe 12.0 +- 1e-9
      result.horizontal.index shouldBe 4
    }

    "treats the stop index as exclusive and the window starts as inclusive" in {
      val series = speedSeries(
        vertical = Vector(1, 1, 1, 1, 1, 1, 1, 1, 100, 1),
        horizontal = Vector(1, 1, 1, 1, 2, 2, 2, 2, 100, 2)
      )

      val result = SwoopMetrics.maxSpeeds(series, diveIdx = 1, topOfTurn = 2, rolloutIdx = 4, stopIdx = 8)

      result.vertical.index shouldBe 2                   // first of the tied 1 m/s values
      result.total.speed shouldBe Math.sqrt(5.0) +- 1e-9 // sqrt(2^2 + 1^2), first of the tied maxima
      result.total.index shouldBe 4
      result.horizontal.index shouldBe 4
    }

    "returns the earliest index when the maximum is tied" in {
      val startTie = speedSeries(Vector(1, 1, 5, 1, 1, 5, 1, 1, 1, 1), Vector.fill(10)(1.0))
      val midTie   = speedSeries(Vector(1, 1, 1, 5, 1, 1, 5, 1, 1, 1), Vector.fill(10)(1.0))

      SwoopMetrics.maxSpeeds(startTie, diveIdx = 1, topOfTurn = 2, rolloutIdx = 4, stopIdx = 8).vertical.index shouldBe
      2
      SwoopMetrics.maxSpeeds(midTie, diveIdx = 1, topOfTurn = 2, rolloutIdx = 4, stopIdx = 8).vertical.index shouldBe 3
    }

    "skips non-positive speeds when scanning" in {
      val series = speedSeries(Vector(1, 1, -5, 1, -1, 2, 1, 1, 1, 1), Vector.fill(10)(1.0))

      SwoopMetrics.maxSpeeds(series, diveIdx = 1, topOfTurn = 2, rolloutIdx = 4, stopIdx = 8).vertical.index shouldBe 5
    }

    "skips NaN speeds when scanning" in {
      val series = speedSeries(Vector(1, 1, 1, Double.NaN, 1, 1, 1, 1, 1, 1), Vector.fill(10)(1.0))

      val result = SwoopMetrics.maxSpeeds(series, diveIdx = 1, topOfTurn = 2, rolloutIdx = 4, stopIdx = 8)

      result.vertical.index shouldBe 2
      result.vertical.speed shouldBe 1.0 +- 1e-9
    }

    "falls back to the dive point when the vertical and total windows are empty" in {
      val series = speedSeries(
        vertical = Vector(1, 3.5, 1, 1, 1, 2, 1, 1, 1, 1),
        horizontal = Vector(1, 1, 1, 1, 1, 9, 1, 1, 1, 1)
      )

      val emptyAtStop   = SwoopMetrics.maxSpeeds(series, diveIdx = 1, topOfTurn = 5, rolloutIdx = 5, stopIdx = 5)
      val emptyReversed = SwoopMetrics.maxSpeeds(series, diveIdx = 1, topOfTurn = 6, rolloutIdx = 5, stopIdx = 5)

      def check(result: SwoopMetrics.MaxSpeeds): Unit = {
        result.vertical.speed shouldBe 3.5 +- 1e-9
        result.vertical.index shouldBe 1
        result.total.speed shouldBe Math.sqrt(13.25) +- 1e-9 // sqrt(3.5^2 + 1^2) at the dive point
        result.total.index shouldBe 1
        result.horizontal.speed shouldBe 9.0 +- 1e-9
        result.horizontal.index shouldBe 5
      }

      check(emptyAtStop)
      check(emptyReversed)
    }

    "falls back to the rollout point when no horizontal speed in the window is positive" in {
      //  Horizontal speed is a magnitude, so it is never negative: the only way the scan finds nothing is a window
      //  of exact zeros.
      val series = speedSeries(
        vertical = Vector(1, 1, 2, 3, 1, 7, 1, 8, 1, 1),
        horizontal = Vector(1, 1, 1, 1, 0, 0, 0, 0, 1, 1)
      )

      val result = SwoopMetrics.maxSpeeds(series, diveIdx = 1, topOfTurn = 2, rolloutIdx = 4, stopIdx = 8)

      result.horizontal.speed shouldBe 0.0 +- 1e-9
      result.horizontal.index shouldBe 4
      result.vertical.index shouldBe 7
      result.total.index shouldBe 7
    }

  }

  //  turnDegrees  //

  "SwoopMetrics.turnDegrees" - {

    "sums the per-sample heading deltas over (topOfTurn, bottomIdx]" in {
      val series = turnSeries(Vector(0, 10, 20, 30, 40, 50), vertical = 5.0)

      SwoopMetrics.turnDegrees(series, topOfTurn = 0, bottomIdx = 5) shouldBe 50.0 +- 1e-9
      SwoopMetrics.turnDegrees(series, topOfTurn = 2, bottomIdx = 4) shouldBe 20.0 +- 1e-9
      SwoopMetrics.turnDegrees(series, topOfTurn = 3, bottomIdx = 4) shouldBe 10.0 +- 1e-9
    }

    "returns zero when the window contains no samples" in {
      val series = turnSeries(Vector(0, 10, 20, 30), vertical = 5.0)

      SwoopMetrics.turnDegrees(series, topOfTurn = 3, bottomIdx = 3) shouldBe 0.0 +- 1e-9
      SwoopMetrics.turnDegrees(series, topOfTurn = 0, bottomIdx = 0) shouldBe 0.0 +- 1e-9
      SwoopMetrics.turnDegrees(series, topOfTurn = 3, bottomIdx = 1) shouldBe 0.0 +- 1e-9
    }

    "is negative for left turns" in {
      val series = turnSeries(Vector(50, 40, 30, 20, 10, 0), vertical = 5.0)

      SwoopMetrics.turnDegrees(series, topOfTurn = 0, bottomIdx = 5) shouldBe -50.0 +- 1e-9
    }

    "wraps heading deltas across north" in {
      val right = turnSeries(Vector(350, 10), vertical = 5.0)
      val left  = turnSeries(Vector(10, 350), vertical = 5.0)

      SwoopMetrics.turnDegrees(right, topOfTurn = 0, bottomIdx = 1) shouldBe 20.0 +- 1e-9
      SwoopMetrics.turnDegrees(left, topOfTurn = 0, bottomIdx = 1) shouldBe -20.0 +- 1e-9
    }

    "resolves an opposite-direction wobble inside a steep dive into the established turn direction" in {
      //  A right run at 10 deg/sample, then four samples appearing to turn left by 15 deg/sample inside a steep fast
      //  dive (30 m/s down at 10 m/s horizontal), then back to the right run. The unwrap pass replaces the wobble
      //  with dt-weighted shares of the net heading change resolved into the pre-wobble (right) direction:
      //  330 - 30 = +300 deg, spread as 4 x 75 deg.
      val series = turnSeries(Vector(0, 10, 20, 30, 15, 0, 345, 330, 335), vertical = 30.0)

      SwoopMetrics.turnDegrees(series, topOfTurn = 0, bottomIdx = 3) shouldBe 30.0 +- 1e-6  // right run, untouched
      SwoopMetrics.turnDegrees(series, topOfTurn = 0, bottomIdx = 8) shouldBe 335.0 +- 1e-6 // 30 + 300 + 5
    }

    "keeps the raw wobble angles outside a steep dive" in {
      //  Same headings as the resolved wobble, but cruising: the dive angle (atan(5/10) ~ 26.6 deg) is below the
      //  60 deg cap, so no segment opens and the raw -15 deg samples are summed.
      val series = turnSeries(Vector(0, 10, 20, 30, 15, 0, 345, 330, 335), vertical = 5.0)

      SwoopMetrics.turnDegrees(series, topOfTurn = 0, bottomIdx = 8) shouldBe -25.0 +- 1e-9
    }

    "keeps the raw wobble when the resolved change reaches the segment angle cap" in {
      //  A two-sample wobble of -30 deg resolves to -30 + 360 = +330 deg, at the 315 deg cap, so the raw angles are
      //  kept: 10 + 10 + 10 - 15 - 15 + 5.
      val series = turnSeries(Vector(0, 10, 20, 30, 15, 0, 5), vertical = 30.0)

      SwoopMetrics.turnDegrees(series, topOfTurn = 0, bottomIdx = 6) shouldBe 5.0 +- 1e-9
    }

    "keeps the raw wobble when the segment displaces the track beyond the displacement cap" in {
      //  One-second samples at 10 m/s dead-reckon 10 m of displacement per sample, over the 9.144 m cap, so the
      //  segment closes on its first sample and its angles are never replaced.
      val series = turnSeries(Vector(0, 10, 20, 30, 15, 0, 345, 330, 335), vertical = 30.0, dtMillis = 1000.0)

      SwoopMetrics.turnDegrees(series, topOfTurn = 0, bottomIdx = 8) shouldBe -25.0 +- 1e-9
    }

  }

}
