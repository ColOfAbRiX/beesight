package com.colofabrix.scala.stats

import munit.CatsEffectSuite
import com.colofabrix.scala.stats.Calculus.TimedValue
import com.colofabrix.scala.stats.PhysicsCalculator.DetectorState
import java.time.Instant
import scala.collection.immutable.Queue

class PhysicsDetectorSpec extends CatsEffectSuite {

  test("PhysicsDetector - when in Empty state - should transition to Filling state with one data point") {
    val detector     = PhysicsCalculator()
    val initialState = DetectorState.Empty
    val time         = Instant.parse("2023-01-01T00:00:00Z")
    val value        = 100.0

    val expected = DetectorState.Filling(Queue(TimedValue(value, time)))

    val actual = detector.checkNextValue(initialState, value, time)

    assertEquals(actual, expected)
  }

  test("PhysicsDetector - when in Filling state with one data point - should remain in Filling state with two data points") {
    val detector     = PhysicsCalculator()
    val time1        = Instant.parse("2023-01-01T00:00:00Z")
    val time2        = Instant.parse("2023-01-01T00:00:01Z")
    val value1       = 100.0
    val value2       = 110.0
    val initialState = DetectorState.Filling(Queue(TimedValue(value1, time1)))

    val expected = DetectorState.Filling(Queue(TimedValue(value1, time1), TimedValue(value2, time2)))

    val actual = detector.checkNextValue(initialState, value2, time2)

    assertEquals(actual, expected)
  }

  test("PhysicsDetector - when in Filling state with two data points - should transition to Detection state with three data points") {
    val detector     = PhysicsCalculator()
    val time1        = Instant.parse("2023-01-01T00:00:00Z")
    val time2        = Instant.parse("2023-01-01T00:00:01Z")
    val time3        = Instant.parse("2023-01-01T00:00:02Z")
    val value1       = 100.0
    val value2       = 110.0
    val value3       = 125.0
    val initialState = DetectorState.Filling(Queue(TimedValue(value1, time1), TimedValue(value2, time2)))

    val speed1       = (value2 - value1) / 1.0 // 10.0 m/s
    val speed2       = (value3 - value2) / 1.0 // 15.0 m/s
    val acceleration = (speed2 - speed1) / 1.0 // 5.0 m/s²

    val expected =
      DetectorState.Detection(
        previous = Queue(TimedValue(value2, time2), TimedValue(value3, time3)),
        time = time3,
        value = value3,
        speed = speed2,
        acceleration = acceleration,
      )

    val actual = detector.checkNextValue(initialState, value3, time3)

    assertEquals(actual, expected)
  }

  test("PhysicsDetector - when in Detection state - should update speed and acceleration with new data point") {
    val detector = PhysicsCalculator()
    val time1    = Instant.parse("2023-01-01T00:00:00Z")
    val time2    = Instant.parse("2023-01-01T00:00:01Z")
    val time3    = Instant.parse("2023-01-01T00:00:02Z")
    val time4    = Instant.parse("2023-01-01T00:00:03Z")
    val value1   = 100.0
    val value2   = 110.0
    val value3   = 125.0
    val value4   = 145.0

    val initialState =
      DetectorState.Detection(
        previous = Queue(TimedValue(value2, time2), TimedValue(value3, time3)),
        time = time3,
        value = value3,
        acceleration = 5.0, // ((value3 - value2) - (value2 - value1)) / 1.0
      )

    val speed3       = (value4 - value3) / 1.0 // 20.0 m/s
    val speed2       = (value3 - value2) / 1.0 // 15.0 m/s
    val acceleration = (speed3 - speed2) / 1.0 // 5.0 m/s²

    val expected =
      DetectorState.Detection(
        previous = Queue(TimedValue(value3, time3), TimedValue(value4, time4)),
        time = time4,
        value = value4,
        speed = speed3,
        acceleration = acceleration,
      )

    val actual = detector.checkNextValue(initialState, value4, time4)

    assertEquals(actual, expected)
  }

  test("PhysicsDetector - with real-world data - should calculate correct speed and acceleration") {
    val detector = PhysicsCalculator()
    val time1    = Instant.parse("2023-01-01T00:00:00Z")
    val time2    = Instant.parse("2023-01-01T00:00:01Z")
    val time3    = Instant.parse("2023-01-01T00:00:02Z")
    val time4    = Instant.parse("2023-01-01T00:00:03Z")
    val time5    = Instant.parse("2023-01-01T00:00:04Z")

    val height1 = 1000.0 // meters
    val height2 = 990.0  // falling at 10 m/s
    val height3 = 970.0  // falling at 20 m/s (acceleration of 10 m/s²)
    val height4 = 940.0  // falling at 30 m/s (acceleration of 10 m/s²)
    val height5 = 900.0  // falling at 40 m/s (acceleration of 10 m/s²)

    val state1 = detector.checkNextValue(DetectorState.Empty, height1, time1)
    val state2 = detector.checkNextValue(state1, height2, time2)
    val state3 = detector.checkNextValue(state2, height3, time3)
    val state4 = detector.checkNextValue(state3, height4, time4)
    val state5 = detector.checkNextValue(state4, height5, time5)

    val expectedSpeed        = -40.0 // m/s (falling at 40 m/s)
    val expectedAcceleration = -10.0 // m/s² (acceleration of 10 m/s²)

    state5 match {
      case DetectorState.Detection(_, _, _, speed, acceleration) =>
        assertEquals(speed, expectedSpeed)
        assertEquals(acceleration, expectedAcceleration)

      case _ =>
        fail("Expected Detection state but got something else")
    }
  }

  test("PhysicsDetector - with edge cases - should handle zero time difference") {
    val detector = PhysicsCalculator()
    val time1    = Instant.parse("2023-01-01T00:00:00Z")
    val time2    = Instant.parse("2023-01-01T00:00:01Z")
    val time3    = time2 // Same time as previous point
    val value1   = 100.0
    val value2   = 110.0
    val value3   = 120.0

    val initialState = DetectorState.Filling(Queue(TimedValue(value1, time1), TimedValue(value2, time2)))

    intercept[ArithmeticException] {
      detector.checkNextValue(initialState, value3, time3)
    }
  }

  test("PhysicsDetector - with edge cases - should handle negative time difference") {
    val detector = PhysicsCalculator()
    val time1    = Instant.parse("2023-01-01T00:00:00Z")
    val time2    = Instant.parse("2023-01-01T00:00:01Z")
    val time3    = Instant.parse("2023-01-01T00:00:00.500Z") // Earlier than time2
    val value1   = 100.0
    val value2   = 110.0
    val value3   = 120.0

    val initialState = DetectorState.Filling(Queue(TimedValue(value1, time1), TimedValue(value2, time2)))

    val speed2       = (value3 - value2) / -0.5 // -20.0 m/s
    val speed1       = (value2 - value1) / 1.0  // 10.0 m/s
    val acceleration = (speed2 - speed1) / -0.5 // 60.0 m/s²

    val expected =
      DetectorState.Detection(
        previous = Queue(TimedValue(value2, time2), TimedValue(value3, time3)),
        time = time3,
        value = value3,
        speed = speed2,
        acceleration = acceleration,
      )

    val actual = detector.checkNextValue(initialState, value3, time3)

    assertEquals(actual, expected)
  }

}
