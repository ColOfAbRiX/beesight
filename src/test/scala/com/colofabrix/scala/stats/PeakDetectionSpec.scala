package com.colofabrix.scala.stats

import cats.effect.IO
import com.colofabrix.scala.stats.PeakDetection.Peak
import fs2.*
import munit.CatsEffectSuite

class PeakDetectionSpec extends CatsEffectSuite {

  test("PeakDetection should return the peaks") {
    val data =
      List(
        1.0, 1.0, 1.1, 1.0, 0.9, 1.0, 1.0, 1.1, 1.0, 0.9, 1.0, 1.1, 1.0, 1.0, 0.9, 1.0, 1.0, 1.1, 1.0, 1.0, 1.0, 1.0,
        1.1, 0.9, 1.0, 1.1, 1.0, 1.0, 0.9, 1.0, 1.1, 1.0, 1.0, 1.1, 1.0, 0.8, 0.9, 1.0, 1.2, 0.9, 1.0, 1.0, 1.1, 1.2,
        1.0, 1.5, 1.0, 3.0, 2.0, 5.0, 3.0, 2.0, 1.0, 1.0, 1.0, 0.9, 1.0, 1.0, 3.0, 2.6, 4.0, 3.0, 3.2, 2.0, 1.0, 1.0,
        0.8, 4.0, 4.0, 2.0, 2.5, 1.0, 1.0, 1.0, -4.0, -4.0,
      )

    val expected =
      List(
        (1.1, Peak.Stable),
        (1.0, Peak.Stable),
        (1.0, Peak.Stable),
        (1.1, Peak.Stable),
        (1.0, Peak.Stable),
        (0.8, Peak.Stable),
        (0.9, Peak.Stable),
        (1.0, Peak.Stable),
        (1.2, Peak.Stable),
        (0.9, Peak.Stable),
        (1.0, Peak.Stable),
        (1.0, Peak.Stable),
        (1.1, Peak.Stable),
        (1.2, Peak.Stable),
        (1.0, Peak.Stable),
        (1.5, Peak.PositivePeak),
        (1.0, Peak.Stable),
        (3.0, Peak.PositivePeak),
        (2.0, Peak.Stable),
        (5.0, Peak.PositivePeak),
        (3.0, Peak.Stable),
        (2.0, Peak.Stable),
        (1.0, Peak.Stable),
        (1.0, Peak.Stable),
        (1.0, Peak.Stable),
        (0.9, Peak.Stable),
        (1.0, Peak.Stable),
        (1.0, Peak.Stable),
        (3.0, Peak.Stable),
        (2.6, Peak.Stable),
        (4.0, Peak.Stable),
        (3.0, Peak.Stable),
        (3.2, Peak.Stable),
        (2.0, Peak.Stable),
        (1.0, Peak.Stable),
        (1.0, Peak.Stable),
        (0.8, Peak.Stable),
        (4.0, Peak.Stable),
        (4.0, Peak.Stable),
        (2.0, Peak.Stable),
        (2.5, Peak.Stable),
        (1.0, Peak.Stable),
        (1.0, Peak.Stable),
        (1.0, Peak.Stable),
        (-4.0, Peak.NegativePeak),
        (-4.0, Peak.Stable),
      )

    val detector = PeakDetection(30, 5.0, 0.5)

    val actual =
      Stream
        .emits(data)
        .through(detector.detect[Double](identity))
        .compile
        .toList

    assertIO(actual, expected)
  }

}
