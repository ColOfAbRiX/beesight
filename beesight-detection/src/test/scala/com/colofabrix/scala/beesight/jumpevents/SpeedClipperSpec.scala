package com.colofabrix.scala.beesight.jumpevents

import cats.effect.IO
import com.colofabrix.scala.beesight.IOValues
import com.colofabrix.scala.beesight.config.*
import com.colofabrix.scala.beesight.model.*
import fs2.Stream
import io.github.iltotore.iron.*
import java.time.Instant
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class SpeedClipperSpec extends AnyFreeSpec with Matchers with IOValues {

  "SpeedClipper" - {

    "preprocess pipe" - {

      "should delay output by window size" in {
        val config = buildConfig(preprocessWindowSize = 3)
        val points = (1 to 5).map(i => point(i.toLong)).toList

        val expected = (1 to 5).map(i => point(i.toLong)).toList
        val actual   = runPreprocess(points, config)

        actual.map(extract) shouldBe expected.map(p => (p.time, p.altitude, p.speed))
      }

      "should output points in order" in {
        val config = buildConfig(preprocessWindowSize = 3)
        val points = (1 to 10).map(i => point(i.toLong)).toList

        val expected = (1 to 10).map(i => point(i.toLong)).toList
        val actual   = runPreprocess(points, config)

        actual.map(extract) shouldBe expected.map(p => (p.time, p.altitude, p.speed))
      }

      "should handle minimum window size of 2" in {
        val config = buildConfig(preprocessWindowSize = 1)
        val points = (1 to 5).map(i => point(i.toLong)).toList

        val expected = (1 to 5).map(i => point(i.toLong)).toList
        val actual   = runPreprocess(points, config)

        actual.map(extract) shouldBe expected.map(p => (p.time, p.altitude, p.speed))
      }

      "should pass through normal data unchanged" in {
        val config = buildConfig(preprocessWindowSize = 3, accelerationClip = 100.0)
        val points =
          List(
            point(1, altitude = 1000.0, verticalSpeed = 10.0),
            point(2, altitude = 990.0, verticalSpeed = 10.0),
            point(3, altitude = 980.0, verticalSpeed = 10.0),
            point(4, altitude = 970.0, verticalSpeed = 10.0),
            point(5, altitude = 960.0, verticalSpeed = 10.0),
          )

        val expected =
          List(
            point(1, altitude = 1000.0, verticalSpeed = 10.0),
            point(2, altitude = 990.0, verticalSpeed = 10.0),
            point(3, altitude = 980.0, verticalSpeed = 10.0),
            point(4, altitude = 970.0, verticalSpeed = 10.0),
            point(5, altitude = 960.0, verticalSpeed = 10.0),
          )
        val actual = runPreprocess(points, config)

        actual.map(extract) shouldBe expected.map(p => (p.time, p.altitude, p.speed))
      }

      "should handle empty input" in {
        val expected = List.empty[(Instant, Double, GeoVector)]
        val actual   = runPreprocess(List.empty)

        actual.map(extract) shouldBe expected
      }

      "should handle single point input" in {
        val expected = List(point(1))
        val actual   = runPreprocess(List(point(1)))

        actual.map(extract) shouldBe expected.map(p => (p.time, p.altitude, p.speed))
      }

      "should handle input smaller than window" in {
        val config = buildConfig(preprocessWindowSize = 5)
        val points = List(point(1), point(2), point(3))

        val expected = List(point(1), point(2), point(3))
        val actual   = runPreprocess(points, config)

        actual.map(extract) shouldBe expected.map(p => (p.time, p.altitude, p.speed))
      }

      "should handle exact window size input" in {
        val config = buildConfig(preprocessWindowSize = 3)
        val points = List(point(1), point(2), point(3))

        val expected = List(point(1), point(2), point(3))
        val actual   = runPreprocess(points, config)

        actual.map(extract) shouldBe expected.map(p => (p.time, p.altitude, p.speed))
      }

      "should handle window size + 1 input" in {
        val config = buildConfig(preprocessWindowSize = 3)
        val points = List(point(1), point(2), point(3), point(4))

        val expected = List(point(1), point(2), point(3), point(4))
        val actual   = runPreprocess(points, config)

        actual.map(extract) shouldBe expected.map(p => (p.time, p.altitude, p.speed))
      }

    }

    "despiking" - {

      "should interpolate spikes when acceleration exceeds threshold" in {
        val config = buildConfig(preprocessWindowSize = 3, accelerationClip = 5.0)
        val points =
          List(
            point(1, altitude = 1000.0, verticalSpeed = 10.0),
            point(2, altitude = 990.0, verticalSpeed = 100.0), // Spike!
            point(3, altitude = 980.0, verticalSpeed = 10.0),
            point(4, altitude = 970.0, verticalSpeed = 10.0),
            point(5, altitude = 960.0, verticalSpeed = 10.0),
            point(6, altitude = 950.0, verticalSpeed = 10.0),
          )

        // Point 2 spike is clipped via interpolation
        val expected =
          List(
            point(1, altitude = 1000.0, verticalSpeed = 10.0),
            point(2, altitude = 990.0, verticalSpeed = 10.0),
            point(3, altitude = 980.0, verticalSpeed = 10.0),
            point(4, altitude = 970.0, verticalSpeed = 10.0),
            point(5, altitude = 960.0, verticalSpeed = 10.0),
            point(6, altitude = 950.0, verticalSpeed = 10.0),
          )

        val actual = runPreprocess(points, config)

        actual.map(extract) shouldBe expected.map(p => (p.time, p.altitude, p.speed))
      }

      "should not modify data when acceleration is below threshold" in {
        val config = buildConfig(preprocessWindowSize = 3, accelerationClip = 1000.0)
        val points =
          List(
            point(1, altitude = 1000.0, verticalSpeed = 10.0),
            point(2, altitude = 990.0, verticalSpeed = 20.0),
            point(3, altitude = 980.0, verticalSpeed = 10.0),
            point(4, altitude = 970.0, verticalSpeed = 10.0),
            point(5, altitude = 960.0, verticalSpeed = 10.0),
          )

        val expected =
          List(
            point(1, altitude = 1000.0, verticalSpeed = 10.0),
            point(2, altitude = 990.0, verticalSpeed = 20.0),
            point(3, altitude = 980.0, verticalSpeed = 10.0),
            point(4, altitude = 970.0, verticalSpeed = 10.0),
            point(5, altitude = 960.0, verticalSpeed = 10.0),
          )

        val actual = runPreprocess(points, config)

        actual.map(extract) shouldBe expected.map(p => (p.time, p.altitude, p.speed))
      }

      "should handle multi-point spike lasting 4 points" in {
        val config = buildConfig(preprocessWindowSize = 5, accelerationClip = 5.0)
        val points =
          List(
            point(1, altitude = 1000.0, verticalSpeed = 10.0),
            point(2, altitude = 990.0, verticalSpeed = 11.0),
            point(3, altitude = 980.0, verticalSpeed = 100.0), // Spike start
            point(4, altitude = 970.0, verticalSpeed = 100.0), // Spike continues
            point(5, altitude = 960.0, verticalSpeed = 100.0), // Spike continues
            point(6, altitude = 950.0, verticalSpeed = 100.0), // Spike end
            point(7, altitude = 940.0, verticalSpeed = 16.0),
            point(8, altitude = 930.0, verticalSpeed = 17.0),
            point(9, altitude = 920.0, verticalSpeed = 18.0),
            point(10, altitude = 910.0, verticalSpeed = 19.0),
            point(11, altitude = 900.0, verticalSpeed = 20.0),
            point(12, altitude = 890.0, verticalSpeed = 21.0),
            point(13, altitude = 880.0, verticalSpeed = 22.0),
            point(14, altitude = 870.0, verticalSpeed = 23.0),
            point(15, altitude = 860.0, verticalSpeed = 24.0),
          )

        val expected =
          List(
            point(1, altitude = 1000.0, verticalSpeed = 10.0),
            point(2, altitude = 990.0, verticalSpeed = 32.5),
            point(3, altitude = 980.0, verticalSpeed = 49.375),
            point(4, altitude = 970.0, verticalSpeed = 41.03125),
            point(5, altitude = 960.0, verticalSpeed = 35.0234375),
            point(6, altitude = 950.0, verticalSpeed = 30.767578125),
            point(7, altitude = 940.0, verticalSpeed = 27.82568359375),
            point(8, altitude = 930.0, verticalSpeed = 25.8692626953125),
            point(9, altitude = 920.0, verticalSpeed = 24.651947021484375),
            point(10, altitude = 910.0, verticalSpeed = 19.0),
            point(11, altitude = 900.0, verticalSpeed = 20.0),
            point(12, altitude = 890.0, verticalSpeed = 21.0),
            point(13, altitude = 880.0, verticalSpeed = 22.0),
            point(14, altitude = 870.0, verticalSpeed = 23.0),
            point(15, altitude = 860.0, verticalSpeed = 24.0),
          )

        val actual = runPreprocess(points, config)

        actual.map(extract) shouldEqual expected.map(p => (p.time, p.altitude, p.speed))
      }

    }

    "consecutive spikes" - {

      "should handle two adjacent spikes" in {
        val config = buildConfig(preprocessWindowSize = 3, accelerationClip = 5.0)
        val points =
          List(
            point(1, verticalSpeed = 10.0),
            point(2, verticalSpeed = 100.0), // Spike 1
            point(3, verticalSpeed = 100.0), // Spike 2
            point(4, verticalSpeed = 10.0),
            point(5, verticalSpeed = 10.0),
            point(6, verticalSpeed = 10.0),
          )

        val actual = runPreprocess(points, config)

        // Spiked points should be smoothed toward neighbors
        extract(actual(1))._3.vertical should be < 100.0
        extract(actual(2))._3.vertical should be < 100.0
        // Non-spike points should be preserved
        extract(actual(0))._3.vertical shouldBe 10.0
      }

    }

    "spike at stream boundaries" - {

      "should handle spike as first point" in {
        val config = buildConfig(preprocessWindowSize = 3, accelerationClip = 5.0)
        val points =
          List(
            point(1, verticalSpeed = 100.0), // Spike at start
            point(2, verticalSpeed = 10.0),
            point(3, verticalSpeed = 10.0),
            point(4, verticalSpeed = 10.0),
            point(5, verticalSpeed = 10.0),
          )

        val actual = runPreprocess(points, config)

        actual.size shouldBe 5
      }

      "should handle spike as last point" in {
        val config = buildConfig(preprocessWindowSize = 3, accelerationClip = 5.0)
        val points =
          List(
            point(1, verticalSpeed = 10.0),
            point(2, verticalSpeed = 10.0),
            point(3, verticalSpeed = 10.0),
            point(4, verticalSpeed = 10.0),
            point(5, verticalSpeed = 100.0), // Spike at end
          )

        val actual = runPreprocess(points, config)

        actual.size shouldBe 5
      }

    }

    "negative speeds" - {

      "should preserve negative speeds without despiking when below threshold" in {
        val config = buildConfig(preprocessWindowSize = 3, accelerationClip = 1000.0)
        val points =
          List(
            point(1, verticalSpeed = -10.0),
            point(2, verticalSpeed = -20.0),
            point(3, verticalSpeed = -30.0),
            point(4, verticalSpeed = -40.0),
            point(5, verticalSpeed = -50.0),
          )

        val actual = runPreprocess(points, config)

        actual.map(extract).zip(points).foreach {
          case (a, e) =>
            a._3.vertical shouldBe e.speed.vertical
        }
      }

    }

    "non-uniform time spacing" - {

      "should handle irregular time intervals" in {
        val config = buildConfig(preprocessWindowSize = 3, accelerationClip = 1000.0)
        val points =
          List(
            point(1, verticalSpeed = 10.0),
            point(3, verticalSpeed = 10.0),   // 2-second gap
            point(10, verticalSpeed = 10.0),  // 7-second gap
            point(11, verticalSpeed = 10.0),  // 1-second gap
            point(100, verticalSpeed = 10.0), // 89-second gap
          )

        val actual = runPreprocess(points, config)

        actual.size shouldBe 5
        // All have the same speed, so no despiking needed
        actual.map(extract).foreach(_._3.vertical shouldBe 10.0)
      }

    }

    "window sizes" - {

      "should work with large window size" in {
        val config = buildConfig(preprocessWindowSize = 10)
        val points = (1 to 15).map(i => point(i.toLong)).toList

        val expected = (1 to 15).map(i => point(i.toLong)).toList
        val actual   = runPreprocess(points, config)

        actual.map(extract) shouldBe expected.map(p => (p.time, p.altitude, p.speed))
      }

      "should work with window size 2" in {
        val config = buildConfig(preprocessWindowSize = 2)
        val points = (1 to 5).map(i => point(i.toLong)).toList

        val expected = (1 to 5).map(i => point(i.toLong)).toList
        val actual   = runPreprocess(points, config)

        actual.map(extract) shouldBe expected.map(p => (p.time, p.altitude, p.speed))
      }

    }

    "velocity and altitude patterns" - {

      "should preserve varying altitudes" in {
        val config = buildConfig(preprocessWindowSize = 3, accelerationClip = 1000.0)
        val points =
          List(
            point(1, altitude = 5000.0),
            point(2, altitude = 4500.0),
            point(3, altitude = 4000.0),
            point(4, altitude = 3500.0),
            point(5, altitude = 3000.0),
          )

        val expected =
          List(
            point(1, altitude = 5000.0),
            point(2, altitude = 4500.0),
            point(3, altitude = 4000.0),
            point(4, altitude = 3500.0),
            point(5, altitude = 3000.0),
          )

        val actual = runPreprocess(points, config)

        actual.map(extract) shouldBe expected.map(p => (p.time, p.altitude, p.speed))
      }

      "should preserve varying speeds in all directions" in {
        val config = buildConfig(preprocessWindowSize = 3, accelerationClip = 1000.0)
        val points =
          List(
            point(1, northSpeed = 10.0, eastSpeed = 5.0, verticalSpeed = -2.0),
            point(2, northSpeed = 15.0, eastSpeed = 8.0, verticalSpeed = -3.0),
            point(3, northSpeed = 20.0, eastSpeed = 12.0, verticalSpeed = -5.0),
            point(4, northSpeed = 25.0, eastSpeed = 15.0, verticalSpeed = -8.0),
            point(5, northSpeed = 30.0, eastSpeed = 18.0, verticalSpeed = -10.0),
          )

        val expected =
          List(
            point(1, northSpeed = 10.0, eastSpeed = 5.0, verticalSpeed = -2.0),
            point(2, northSpeed = 15.0, eastSpeed = 8.0, verticalSpeed = -3.0),
            point(3, northSpeed = 20.0, eastSpeed = 12.0, verticalSpeed = -5.0),
            point(4, northSpeed = 25.0, eastSpeed = 15.0, verticalSpeed = -8.0),
            point(5, northSpeed = 30.0, eastSpeed = 18.0, verticalSpeed = -10.0),
          )

        val actual = runPreprocess(points, config)

        actual.map(extract) shouldBe expected.map(p => (p.time, p.altitude, p.speed))
      }

      "should handle large stream of points" in {
        val config = buildConfig(preprocessWindowSize = 5)
        val points = (1 to 100).map(i => point(i.toLong, altitude = 1000.0 - i * 10)).toList

        val expected = (1 to 100).map(i => point(i.toLong, altitude = 1000.0 - i * 10)).toList
        val actual   = runPreprocess(points, config)

        actual.map(extract) shouldBe expected.map(p => (p.time, p.altitude, p.speed))
      }

    }

  }

  private def buildConfig(
    preprocessWindowSize: Int = BeesightConfig.default.jumpevents.preprocessWindowSize.value,
    accelerationClip: Double = BeesightConfig.default.jumpevents.accelerationClip.value,
  ): BeesightConfig =
    BeesightConfig.default.copy(
      jumpevents = BeesightConfig.default.jumpevents.copy(
        preprocessWindowSize = WindowSize(preprocessWindowSize.refineUnsafe),
        accelerationClip = Speed(accelerationClip),
      ),
    )

  private def point(
    seconds: Long,
    altitude: Double = 1000.0,
    northSpeed: Double = 0.0,
    eastSpeed: Double = 0.0,
    verticalSpeed: Double = 0.0,
  ): IndexedPoint =
    IndexedPoint(
      idx = seconds,
      time = Instant.ofEpochSecond(seconds),
      altitude = altitude,
      speed = GeoVector(north = northSpeed, east = eastSpeed, vertical = verticalSpeed),
    )

  private def runPreprocess(
    points: List[IndexedPoint],
    config: BeesightConfig = BeesightConfig.default,
  ): List[IndexedPoint] =
    Stream
      .emits[IO, IndexedPoint](points)
      .through(SpeedClipper(config.jumpevents).clip)
      .compile
      .toList
      .result()

  private def extract(dp: IndexedPoint): (Instant, Double, GeoVector) =
    (dp.time, dp.altitude, dp.speed)

}
