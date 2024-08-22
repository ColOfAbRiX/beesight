package com.colofabrix.scala.beesight

import fs2.*
import munit.FunSuite
import com.colofabrix.scala.beesight.PeakDetection.Peak

class PeakDetectionSpec extends FunSuite {

  test("Example test that succeeds") {
    val data =
      List(
        1, 1, 1.1, 1, 0.9, 1, 1, 1.1, 1, 0.9, 1, 1.1, 1, 1, 0.9, 1, 1, 1.1, 1, 1, 1, 1, 1.1, 0.9, 1, 1.1, 1, 1, 0.9, 1,
        1.1, 1, 1, 1.1, 1, 0.8, 0.9, 1, 1.2, 0.9, 1, 1, 1.1, 1.2, 1, 1.5, 1, 3, 2, 5, 3, 2, 1, 1, 1, 0.9, 1, 1, 3, 2.6,
        4, 3, 3.2, 0.001, 0.001, 0.005, 0.8, 4, 4, 2, 2.5, 1, 1, 1)

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
      )

    val detector = PeakDetection(30, 5.0, 0.5)

    val actual =
      Stream
        .emits(data)
        .through(detector.detect)
        .compile
        .toList

    assertEquals(actual, expected)
  }

}
