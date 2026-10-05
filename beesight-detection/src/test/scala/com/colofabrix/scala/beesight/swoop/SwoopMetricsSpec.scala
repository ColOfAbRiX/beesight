package com.colofabrix.scala.beesight.swoop

import com.colofabrix.scala.beesight.config.BeesightConfig
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

  private val config = BeesightConfig.default.swoop

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

    "sums the per-sample rotation over the window" in {
      //  10 deg/sample: every delta inside (top of turn, bottom of turn] counts.
      val series = turnSeries(Vector(0, 10, 20, 30, 40, 50), vertical = 5.0)

      SwoopMetrics.turnDegrees(series, topOfTurn = 0, bottomIdx = 5, config) shouldBe 50.0 +- 1e-9
      SwoopMetrics.turnDegrees(series, topOfTurn = 2, bottomIdx = 4, config) shouldBe 20.0 +- 1e-9
    }

    "is negative for left turns" in {
      val series = turnSeries(Vector(50, 40, 30, 20, 10, 0), vertical = 5.0)

      SwoopMetrics.turnDegrees(series, topOfTurn = 0, bottomIdx = 5, config) shouldBe -50.0 +- 1e-9
    }

    "wraps heading deltas across north" in {
      val right = turnSeries(Vector(350, 10, 30), vertical = 5.0)
      val left  = turnSeries(Vector(10, 350, 330), vertical = 5.0)

      SwoopMetrics.turnDegrees(right, topOfTurn = 0, bottomIdx = 2, config) shouldBe 40.0 +- 1e-9
      SwoopMetrics.turnDegrees(left, topOfTurn = 0, bottomIdx = 2, config) shouldBe -40.0 +- 1e-9
    }

    "returns zero when the window contains no samples" in {
      val series = turnSeries(Vector(0, 10, 20, 30), vertical = 5.0)

      SwoopMetrics.turnDegrees(series, topOfTurn = 3, bottomIdx = 3, config) shouldBe 0.0 +- 1e-9
      SwoopMetrics.turnDegrees(series, topOfTurn = 0, bottomIdx = 0, config) shouldBe 0.0 +- 1e-9
      SwoopMetrics.turnDegrees(series, topOfTurn = 3, bottomIdx = 1, config) shouldBe 0.0 +- 1e-9
    }

    "returns zero when there is no rotation" in {
      val series = turnSeries(Vector.fill(6)(0.0), vertical = 5.0)

      SwoopMetrics.turnDegrees(series, topOfTurn = 0, bottomIdx = 5, config) shouldBe 0.0 +- 1e-9
    }

    "counts slow rotations inside the window: the unwrap pass has no rate threshold" in {
      //  1.5 deg/sample at 100 ms spacing = 15 deg/s: gSwoop counts it; only the drift at the window ends is trimmed.
      val series = turnSeries(Vector(0, 10, 11.5, 13, 25, 35), vertical = 5.0)

      SwoopMetrics.turnDegrees(series, topOfTurn = 0, bottomIdx = 5, config) shouldBe 35.0 +- 1e-9
    }

    "excludes the rotation preceding the window through the top-of-turn boundary" in {
      //  A right-hand drift into the dive followed by the left turn: only the deltas after top of turn count.
      val series = turnSeries(Vector(0, 10, 20, 30, 20, 10, 0, 350, 340, 330), vertical = 5.0)

      SwoopMetrics.turnDegrees(series, topOfTurn = 3, bottomIdx = 9, config) shouldBe -60.0 +- 1e-9
    }

    "excludes a post-turn S-turn through the bottom-of-turn boundary" in {
      //  A left turn followed by a rightward S-turn: the window ends at the bottom of turn, before the S-turn.
      val series = turnSeries(Vector(0, 340, 320, 300, 280, 260, 270, 280, 290, 300), vertical = 5.0)

      SwoopMetrics.turnDegrees(series, topOfTurn = 0, bottomIdx = 5, config) shouldBe -100.0 +- 1e-9
    }

    "reinterprets a counter-rotating run inside a steep fast dive as continuation" in {
      //  Inside a steep fast dive (dive angle > 60 deg, descent > 60 mph) a run turning against the established
      //  direction is resolved into that direction: the -60 deg run becomes +300 deg spread over its samples.
      val series = seriesOf(
        headings = Vector(0, 30, 60, 30, 0, 30),
        horizontal = Vector.fill(6)(10.0),
        vertical = Vector.fill(6)(40.0),
        dtMillis = 100.0
      )

      SwoopMetrics.turnDegrees(series, topOfTurn = 0, bottomIdx = 5, config) shouldBe 390.0 +- 1e-9
    }

    "keeps the raw angles of a counter-rotating run whose resolved change exceeds the segment cap" in {
      //  A -20 deg run resolves to +340 deg, above the 315 deg segment cap: the raw deltas stay.
      val series = seriesOf(
        headings = Vector(0, 30, 60, 50, 40, 70),
        horizontal = Vector.fill(6)(10.0),
        vertical = Vector.fill(6)(40.0),
        dtMillis = 100.0
      )

      SwoopMetrics.turnDegrees(series, topOfTurn = 0, bottomIdx = 5, config) shouldBe 70.0 +- 1e-9
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
