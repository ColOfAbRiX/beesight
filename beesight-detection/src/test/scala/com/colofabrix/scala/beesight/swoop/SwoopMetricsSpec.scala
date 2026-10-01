package com.colofabrix.scala.beesight.swoop

import com.colofabrix.scala.beesight.model.{ GeoVector, IndexedPoint }
import com.colofabrix.scala.beesight.swoop.computations.SwoopMetrics
import com.colofabrix.scala.beesight.swoop.model.PointSeries
import java.time.Instant
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

/**
 * Unit tests for [[SwoopMetrics]].
 */
class SwoopMetricsSpec extends AnyFreeSpec with Matchers {

  //  maxSpeeds  //

  "SwoopMetrics.maxSpeeds" - {

    "selects the maximum speed within each detection window" in {
      //  Vertical peaks at index 7, total at index 5 (sqrt(10^2 + 7^2)), horizontal at index 4; the large speeds at
      //  the stop index must be excluded by the half-open windows.
      val series =
        speedSeries(
          vertical = Vector(1, 1, 2, 3, 1, 7, 1, 8, 100, 1),
          horizontal = Vector(1, 1, 5, 5, 12, 10, 11, 5, 100, 1)
        )

      val result = SwoopMetrics.maxSpeeds(series, diveIdx = 1, topOfTurn = 2, rolloutIdx = 4, stopIdx = 8)

      result shouldBe SwoopMetrics.MaxSpeeds(
        total = SwoopMetrics.MaxSpeed(Math.sqrt(149.0), 5),
        vertical = SwoopMetrics.MaxSpeed(8.0, 7),
        horizontal = SwoopMetrics.MaxSpeed(12.0, 4),
      )
    }

    "treats the stop index as exclusive and the window starts as inclusive" in {
      val series =
        speedSeries(
          vertical = Vector(1, 1, 1, 1, 1, 1, 1, 1, 100, 1),
          horizontal = Vector(1, 1, 1, 1, 2, 2, 2, 2, 100, 2)
        )

      val result = SwoopMetrics.maxSpeeds(series, diveIdx = 1, topOfTurn = 2, rolloutIdx = 4, stopIdx = 8)

      result shouldBe SwoopMetrics.MaxSpeeds(
        total = SwoopMetrics.MaxSpeed(Math.sqrt(5.0), 4),
        vertical = SwoopMetrics.MaxSpeed(1.0, 2),
        horizontal = SwoopMetrics.MaxSpeed(2.0, 4),
      )
    }

    "returns the earliest index when the maximum is tied" in {
      val startTie = speedSeries(Vector(1, 1, 5, 1, 1, 5, 1, 1, 1, 1), Vector.fill(10)(1.0))
      val midTie   = speedSeries(Vector(1, 1, 1, 5, 1, 1, 5, 1, 1, 1), Vector.fill(10)(1.0))

      SwoopMetrics.maxSpeeds(startTie, diveIdx = 1, topOfTurn = 2, rolloutIdx = 4, stopIdx = 8) shouldBe
      SwoopMetrics.MaxSpeeds(
        total = SwoopMetrics.MaxSpeed(Math.sqrt(26.0), 2),
        vertical = SwoopMetrics.MaxSpeed(5.0, 2),
        horizontal = SwoopMetrics.MaxSpeed(1.0, 4),
      )
      SwoopMetrics.maxSpeeds(midTie, diveIdx = 1, topOfTurn = 2, rolloutIdx = 4, stopIdx = 8) shouldBe
      SwoopMetrics.MaxSpeeds(
        total = SwoopMetrics.MaxSpeed(Math.sqrt(26.0), 3),
        vertical = SwoopMetrics.MaxSpeed(5.0, 3),
        horizontal = SwoopMetrics.MaxSpeed(1.0, 4),
      )
    }

    "skips non-positive speeds when scanning" in {
      val series = speedSeries(Vector(1, 1, -5, 1, -1, 2, 1, 1, 1, 1), Vector.fill(10)(1.0))

      SwoopMetrics.maxSpeeds(series, diveIdx = 1, topOfTurn = 2, rolloutIdx = 4, stopIdx = 8) shouldBe
      SwoopMetrics.MaxSpeeds(
        total = SwoopMetrics.MaxSpeed(Math.sqrt(26.0), 2),
        vertical = SwoopMetrics.MaxSpeed(2.0, 5),
        horizontal = SwoopMetrics.MaxSpeed(1.0, 4),
      )
    }

    "skips NaN speeds when scanning" in {
      val series = speedSeries(Vector(1, 1, 1, Double.NaN, 1, 1, 1, 1, 1, 1), Vector.fill(10)(1.0))

      val result = SwoopMetrics.maxSpeeds(series, diveIdx = 1, topOfTurn = 2, rolloutIdx = 4, stopIdx = 8)

      result shouldBe SwoopMetrics.MaxSpeeds(
        total = SwoopMetrics.MaxSpeed(Math.sqrt(2.0), 2),
        vertical = SwoopMetrics.MaxSpeed(1.0, 2),
        horizontal = SwoopMetrics.MaxSpeed(1.0, 4),
      )
    }

    "falls back to the dive point when the vertical and total windows are empty" in {
      val series =
        speedSeries(
          vertical = Vector(1, 3.5, 1, 1, 1, 2, 1, 1, 1, 1),
          horizontal = Vector(1, 1, 1, 1, 1, 9, 1, 1, 1, 1)
        )

      val emptyAtStop   = SwoopMetrics.maxSpeeds(series, diveIdx = 1, topOfTurn = 5, rolloutIdx = 5, stopIdx = 5)
      val emptyReversed = SwoopMetrics.maxSpeeds(series, diveIdx = 1, topOfTurn = 6, rolloutIdx = 5, stopIdx = 5)

      val expected = SwoopMetrics.MaxSpeeds(
        total = SwoopMetrics.MaxSpeed(Math.sqrt(13.25), 1),
        vertical = SwoopMetrics.MaxSpeed(3.5, 1),
        horizontal = SwoopMetrics.MaxSpeed(9.0, 5),
      )

      def check(result: SwoopMetrics.MaxSpeeds): Unit =
        result shouldBe expected

      check(emptyAtStop)
      check(emptyReversed)
    }

    "falls back to the rollout point when no horizontal speed in the window is positive" in {
      //  Horizontal speed is a magnitude, so it is never negative: the only way the scan finds nothing is a window
      //  of exact zeros.
      val series =
        speedSeries(
          vertical = Vector(1, 1, 2, 3, 1, 7, 1, 8, 1, 1),
          horizontal = Vector(1, 1, 1, 1, 0, 0, 0, 0, 1, 1)
        )

      val result = SwoopMetrics.maxSpeeds(series, diveIdx = 1, topOfTurn = 2, rolloutIdx = 4, stopIdx = 8)

      result shouldBe SwoopMetrics.MaxSpeeds(
        total = SwoopMetrics.MaxSpeed(8.0, 7),
        vertical = SwoopMetrics.MaxSpeed(8.0, 7),
        horizontal = SwoopMetrics.MaxSpeed(0.0, 4),
      )
    }

  }

  //  turnDegrees  //

  "SwoopMetrics.turnDegrees" - {

    "measures the net rotation of a sustained carve" in {
      //  10 deg/sample at 100 ms spacing = 100 deg/s, well above the carve threshold: the carve spans the window.
      //  The carve is anchored at the dive point and its backward expansion stops before the top-of-turn boundary,
      //  so the delta from top of turn into the following sample is never part of the turn angle.
      val series = turnSeries(Vector(0, 10, 20, 30, 40, 50), vertical = 5.0)

      SwoopMetrics.turnDegrees(series, topOfTurn = 0, endIdx = 5, diveIdx = 3) shouldBe 40.0 +- 1e-9
      SwoopMetrics.turnDegrees(series, topOfTurn = 2, endIdx = 4, diveIdx = 3) shouldBe 10.0 +- 1e-9
    }

    "is negative for left turns" in {
      val series = turnSeries(Vector(50, 40, 30, 20, 10, 0), vertical = 5.0)

      SwoopMetrics.turnDegrees(series, topOfTurn = 0, endIdx = 5, diveIdx = 3) shouldBe -40.0 +- 1e-9
    }

    "wraps heading deltas across north" in {
      //  Three samples, so the carve window around the dive point is non-degenerate.
      val right = turnSeries(Vector(350, 10, 30), vertical = 5.0)
      val left  = turnSeries(Vector(10, 350, 330), vertical = 5.0)

      SwoopMetrics.turnDegrees(right, topOfTurn = 0, endIdx = 2, diveIdx = 1) shouldBe 20.0 +- 1e-9
      SwoopMetrics.turnDegrees(left, topOfTurn = 0, endIdx = 2, diveIdx = 1) shouldBe -20.0 +- 1e-9
    }

    "returns zero when the window contains no samples" in {
      val series = turnSeries(Vector(0, 10, 20, 30), vertical = 5.0)

      SwoopMetrics.turnDegrees(series, topOfTurn = 3, endIdx = 3, diveIdx = 3) shouldBe 0.0 +- 1e-9
      SwoopMetrics.turnDegrees(series, topOfTurn = 0, endIdx = 0, diveIdx = 0) shouldBe 0.0 +- 1e-9
      SwoopMetrics.turnDegrees(series, topOfTurn = 3, endIdx = 1, diveIdx = 2) shouldBe 0.0 +- 1e-9
    }

    "returns zero when there is no rotation" in {
      val series = turnSeries(Vector.fill(6)(0.0), vertical = 5.0)

      SwoopMetrics.turnDegrees(series, topOfTurn = 0, endIdx = 5, diveIdx = 3) shouldBe 0.0 +- 1e-9
    }

    "returns zero for a slow rotation below the carve threshold" in {
      //  1.5 deg/sample at 100 ms spacing = 15 deg/s: a landing-pattern turn, not a carve.
      val series = turnSeries(Vector(0, 1.5, 3.0, 4.5, 6.0, 7.5), vertical = 5.0)

      SwoopMetrics.turnDegrees(series, topOfTurn = 0, endIdx = 5, diveIdx = 3) shouldBe 0.0 +- 1e-9
    }

    "returns zero when the heading is unreliable at low horizontal speed" in {
      //  Below the reliable-speed floor the heading is undefined noise: large swings must not count as a turn.
      val series = seriesOf(
        headings = Vector(0, 170, 340, 150, 320, 60),
        horizontal = Vector.fill(6)(1.0),
        vertical = Vector.fill(6)(10.0),
        dtMillis = 100.0
      )

      SwoopMetrics.turnDegrees(series, topOfTurn = 0, endIdx = 5, diveIdx = 3) shouldBe 0.0 +- 1e-9
    }

    "excludes the counter-rotation preceding the carve" in {
      //  A right-hand drift into the dive followed by the left carve: only the carve is the turn.
      val series = turnSeries(Vector(0, 10, 20, 30, 20, 10, 0, 350, 340, 330), vertical = 5.0)

      SwoopMetrics.turnDegrees(series, topOfTurn = 0, endIdx = 9, diveIdx = 3) shouldBe -60.0 +- 1e-9
    }

    "excludes a post-carve S-turn from the turn angle" in {
      //  A left carve followed by a rightward S-turn: the carve stops where the left rotation stops and the
      //  S-turn contributes nothing to the turn angle.
      val series = turnSeries(Vector(0, 340, 320, 300, 280, 260, 270, 280, 290, 300), vertical = 5.0)

      SwoopMetrics.turnDegrees(series, topOfTurn = 0, endIdx = 9, diveIdx = 4) shouldBe -80.0 +- 1e-9
    }

  }

  /** A series of points one second apart, heading north, with the given per-index vertical and horizontal speeds. */
  private def speedSeries(vertical: Vector[Double], horizontal: Vector[Double]): PointSeries =
    seriesOf(
      headings = Vector.fill(vertical.length)(0.0),
      horizontal = horizontal,
      vertical = vertical,
      dtMillis = 1000.0
    )

  /** A series of points at 100 ms spacing flying the given headings at a constant speed above the reliable floor. */
  private def turnSeries(
    headings: Vector[Double],
    vertical: Double,
    horizontal: Double = 12.0,
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
            idx = i.toLong,
            time = Instant.ofEpochMilli((i * dtMillis).toLong),
            altitude = 1000.0 - i,
            speed = GeoVector(
              north = horizontal(i) * Math.cos(rad),
              east = horizontal(i) * Math.sin(rad),
              vertical = vertical(i),
            )
          )
        }
        .toVector
    )

}
