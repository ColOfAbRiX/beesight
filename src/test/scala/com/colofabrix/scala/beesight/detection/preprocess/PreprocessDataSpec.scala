package com.colofabrix.scala.beesight.detection.preprocess

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.colofabrix.scala.beesight.config.*
import com.colofabrix.scala.beesight.IOValues
import com.colofabrix.scala.beesight.model.InputFlightRow
import fs2.Stream
import java.time.Instant
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class PreprocessDataSpec extends AnyFreeSpec with Matchers with IOValues {

  "PreprocessData" - {

    "preprocess pipe" - {

      "should delay output by window size" in {
        val config = buildConfig(preprocessWindowSize = 3)
        val points = (1 to 5).map(i => point(i.toLong)).toList

        val expected = List(point(1), point(2))
        val actual   = runPreprocess(points, config)

        actual shouldBe expected
      }

      "should output points in order" in {
        val config = buildConfig(preprocessWindowSize = 3)
        val points = (1 to 10).map(i => point(i.toLong)).toList

        val expected = (1 to 7).map(i => point(i.toLong)).toList
        val actual   = runPreprocess(points, config)

        actual shouldBe expected
      }

      "should handle minimum window size of 2" in {
        val config = buildConfig(preprocessWindowSize = 1)
        val points = (1 to 5).map(i => point(i.toLong)).toList

        val expected = List(point(1), point(2), point(3))
        val actual   = runPreprocess(points, config)

        actual shouldBe expected
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
          )
        val actual = runPreprocess(points, config)

        actual shouldBe expected
      }

      "should handle empty input" in {
        val expected = List.empty[InputFlightRow[Unit]]
        val actual   = runPreprocess(List.empty)

        actual shouldBe expected
      }

      "should handle single point input" in {
        val expected = List.empty[InputFlightRow[Unit]]
        val actual   = runPreprocess(List(point(1)))

        actual shouldBe expected
      }

      "should handle input smaller than window" in {
        val config = buildConfig(preprocessWindowSize = 5)
        val points = List(point(1), point(2), point(3))

        val expected = List.empty[InputFlightRow[Unit]]
        val actual   = runPreprocess(points, config)

        actual shouldBe expected
      }

      "should handle exact window size input" in {
        val config = buildConfig(preprocessWindowSize = 3)
        val points = List(point(1), point(2), point(3))

        val expected = List.empty[InputFlightRow[Unit]]
        val actual   = runPreprocess(points, config)

        actual shouldBe expected
      }

      "should handle window size + 1 input" in {
        val config = buildConfig(preprocessWindowSize = 3)
        val points = List(point(1), point(2), point(3), point(4))

        val expected = List(point(1))
        val actual   = runPreprocess(points, config)

        actual shouldBe expected
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

        // Point 2 spike is passed through (algorithm doesn't modify data in output)
        val expected =
          List(
            point(1, altitude = 1000.0, verticalSpeed = 10.0),
            point(2, altitude = 990.0, verticalSpeed = 10.0),
            point(3, altitude = 980.0, verticalSpeed = 10.0),
          )

        val actual = runPreprocess(points, config)

        actual shouldBe expected
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
          )

        val actual = runPreprocess(points, config)

        actual shouldBe expected
      }

      "should handle multi-point spike lasting 4 points" in {
        val config = buildConfig(preprocessWindowSize = 5, accelerationClip = 5.0)
        val points =
          List(
            point(1, altitude = 1000.0, verticalSpeed = 10.0),
            point(2, altitude = 990.0, verticalSpeed = 10.0),
            point(3, altitude = 980.0, verticalSpeed = 100.0), // Spike start
            point(4, altitude = 970.0, verticalSpeed = 100.0), // Spike continues
            point(5, altitude = 960.0, verticalSpeed = 100.0), // Spike continues
            point(6, altitude = 950.0, verticalSpeed = 100.0), // Spike end
            point(7, altitude = 940.0, verticalSpeed = 10.0),
            point(8, altitude = 930.0, verticalSpeed = 10.0),
            point(9, altitude = 920.0, verticalSpeed = 10.0),
            point(10, altitude = 910.0, verticalSpeed = 10.0),
            point(11, altitude = 900.0, verticalSpeed = 10.0),
            point(12, altitude = 890.0, verticalSpeed = 10.0),
            point(13, altitude = 880.0, verticalSpeed = 10.0),
            point(14, altitude = 870.0, verticalSpeed = 10.0),
            point(15, altitude = 860.0, verticalSpeed = 10.0),
          )

        val expected =
          List(
            point(1, altitude = 1000.0, verticalSpeed = 10.0),
            point(2, altitude = 990.0, verticalSpeed = 10.0),
            point(3, altitude = 980.0, verticalSpeed = 32.5),
            point(4, altitude = 970.0, verticalSpeed = 40.0),
            point(5, altitude = 960.0, verticalSpeed = 100.0),
            point(6, altitude = 950.0, verticalSpeed = 100.0),
            point(7, altitude = 940.0, verticalSpeed = 77.5),
            point(8, altitude = 930.0, verticalSpeed = 10.0),
            point(9, altitude = 920.0, verticalSpeed = 10.0),
            point(10, altitude = 910.0, verticalSpeed = 10.0),
          )

        val actual = runPreprocess(points, config)

        actual shouldEqual expected
      }

    }

    "window sizes" - {

      "should work with large window size" in {
        val config = buildConfig(preprocessWindowSize = 10)
        val points = (1 to 15).map(i => point(i.toLong)).toList

        val expected = (1 to 5).map(i => point(i.toLong)).toList
        val actual   = runPreprocess(points, config)

        actual shouldBe expected
      }

      "should work with window size 2" in {
        val config = buildConfig(preprocessWindowSize = 2)
        val points = (1 to 5).map(i => point(i.toLong)).toList

        val expected = List(point(1), point(2), point(3))
        val actual   = runPreprocess(points, config)

        actual shouldBe expected
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
          )

        val actual = runPreprocess(points, config)

        actual shouldBe expected
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
          )

        val actual = runPreprocess(points, config)

        actual shouldBe expected
      }

      "should handle large stream of points" in {
        val config = buildConfig(preprocessWindowSize = 5)
        val points = (1 to 100).map(i => point(i.toLong, altitude = 1000.0 - i * 10)).toList

        val expected = (1 to 95).map(i => point(i.toLong, altitude = 1000.0 - i * 10)).toList
        val actual   = runPreprocess(points, config)

        actual shouldBe expected
      }

    }

  }

  private def buildConfig(
    preprocessWindowSize: Int = DetectionConfig.default.global.preprocessWindowSize,
    accelerationClip: Double = DetectionConfig.default.global.accelerationClip,
  ): DetectionConfig =
    DetectionConfig.default.copy(
      global = DetectionConfig.default.global.copy(
        preprocessWindowSize = preprocessWindowSize,
        accelerationClip = accelerationClip,
      ),
    )

  private def point(
    seconds: Long,
    altitude: Double = 1000.0,
    northSpeed: Double = 0.0,
    eastSpeed: Double = 0.0,
    verticalSpeed: Double = 0.0,
  ): InputFlightRow[Unit] =
    InputFlightRow(
      time = Instant.ofEpochSecond(seconds),
      altitude = altitude,
      northSpeed = northSpeed,
      eastSpeed = eastSpeed,
      verticalSpeed = verticalSpeed,
      source = (),
    )

  private def runPreprocess(
    points: List[InputFlightRow[Unit]],
    config: DetectionConfig = DetectionConfig.default,
  ): List[InputFlightRow[Unit]] =
    Stream
      .emits[IO, InputFlightRow[Unit]](points)
      .through(PreprocessData[Unit](config).preprocess)
      .compile
      .toList
      .result()

}
