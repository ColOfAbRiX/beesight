package com.colofabrix.scala.beesight.output

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.colofabrix.scala.beesight.config.*
import com.colofabrix.scala.beesight.config.DataCutterConfig.given
import com.colofabrix.scala.beesight.model.*
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class DataCutterSpec extends AnyFreeSpec with Matchers {

  "DataCutter" - {

    "retaining points before takeoff" - {

      "should retain exactly keepBeforeTakeoff points before the takeoff index" in {
        val cfg    = configWith(keepBefore = 5, keepAfter = 50)
        val points =
          annotatedStream(
            50,
            takeoff = Some(eventAt(20)),
            freefall = Some(eventAt(25)),
            landing = Some(eventAt(45))
          )
        val result = runCutter(cfg, points)
        result.count(_.source < 20) shouldBe 5
      }

      "should start emitting from index (takeoff - keepBeforeTakeoff)" in {
        val cfg    = configWith(keepBefore = 3, keepAfter = 50)
        val points =
          annotatedStream(
            40,
            takeoff = Some(eventAt(20)),
            freefall = Some(eventAt(25)),
            landing = Some(eventAt(35))
          )
        val result = runCutter(cfg, points)
        result.head.source shouldBe (20 - 3)
      }

      "should not include points before the keepBeforeTakeoff window" in {
        val cfg    = configWith(keepBefore = 3, keepAfter = 50)
        val points =
          annotatedStream(
            40,
            takeoff = Some(eventAt(20)),
            freefall = Some(eventAt(25)),
            landing = Some(eventAt(35))
          )
        val result = runCutter(cfg, points)
        sources(result) should not contain 16
        sources(result) should not contain 0
      }

      "should clamp to index 0 when keepBeforeTakeoff exceeds the takeoff index" in {
        val cfg    = configWith(keepBefore = 10, keepAfter = 50)
        val points =
          annotatedStream(
            20,
            takeoff = Some(eventAt(3)),
            freefall = Some(eventAt(8)),
            landing = Some(eventAt(15))
          )
        val result = runCutter(cfg, points)
        result.head.source shouldBe 0
      }

      "should retain zero points before takeoff when keepBeforeTakeoff is 0" in {
        val cfg    = configWith(keepBefore = 0, keepAfter = 50)
        val points =
          annotatedStream(
            40,
            takeoff = Some(eventAt(20)),
            freefall = Some(eventAt(25)),
            landing = Some(eventAt(35))
          )
        val result = runCutter(cfg, points)
        result.head.source shouldBe 20
        result.count(_.source < 20) shouldBe 0
      }

      "should handle takeoff at index 0 with keepBeforeTakeoff > 0" in {
        val cfg    = configWith(keepBefore = 5, keepAfter = 50)
        val points =
          annotatedStream(
            30,
            takeoff = Some(eventAt(0)),
            freefall = Some(eventAt(5)),
            landing = Some(eventAt(25))
          )
        val result = runCutter(cfg, points)
        result.head.source shouldBe 0
      }

      "should handle takeoff at index 0 with keepBeforeTakeoff = 0" in {
        val cfg    = configWith(keepBefore = 0, keepAfter = 50)
        val points =
          annotatedStream(
            30,
            takeoff = Some(eventAt(0)),
            freefall = Some(eventAt(5)),
            landing = Some(eventAt(25))
          )
        val result = runCutter(cfg, points)
        result.head.source shouldBe 0
      }

      "should work with keepBeforeTakeoff = 1" in {
        val cfg    = configWith(keepBefore = 1, keepAfter = 50)
        val points =
          annotatedStream(
            40,
            takeoff = Some(eventAt(20)),
            freefall = Some(eventAt(25)),
            landing = Some(eventAt(35))
          )
        val result = runCutter(cfg, points)
        result.head.source shouldBe 19
        result.count(_.source < 20) shouldBe 1
      }

      "should work with large keepBeforeTakeoff covering entire pre-takeoff region" in {
        val cfg    = configWith(keepBefore = 100, keepAfter = 50)
        val points =
          annotatedStream(
            30,
            takeoff = Some(eventAt(10)),
            freefall = Some(eventAt(15)),
            landing = Some(eventAt(25))
          )
        val result = runCutter(cfg, points)
        result.head.source shouldBe 0
        result.count(_.source < 10) shouldBe 10
      }

    }

    "retaining all points when no takeoff is detected" - {

      "should keep all points when takeoff is None" in {
        val cfg    = configWith(keepBefore = 5, keepAfter = 5)
        val points = annotatedStream(20, takeoff = None, freefall = Some(eventAt(5)))
        val result = runCutter(cfg, points)
        result.size shouldBe 20
        sources(result) shouldBe (0 until 20).toVector
      }

      "should keep all points when takeoff is None and stream is small" in {
        val cfg    = configWith(keepBefore = 5, keepAfter = 5)
        val points = annotatedStream(3, takeoff = None, freefall = Some(eventAt(1)))
        val result = runCutter(cfg, points)
        result.size shouldBe 3
      }

      "should keep all points when takeoff is None and landing is also None" in {
        val cfg    = configWith(keepBefore = 5, keepAfter = 5)
        val points = annotatedStream(15, takeoff = None, freefall = Some(eventAt(3)), landing = None)
        val result = runCutter(cfg, points)
        result.size shouldBe 15
      }

    }

    "retaining points after landing" - {

      "should retain keepAfterLanding points after the landing index" in {
        val cfg    = configWith(keepBefore = 50, keepAfter = 5)
        val points =
          annotatedStream(
            50,
            takeoff = Some(eventAt(5)),
            freefall = Some(eventAt(10)),
            landing = Some(eventAt(30))
          )
        val result = runCutter(cfg, points)
        result.count(_.source > 30) shouldBe 5
      }

      "should stop emitting after keepAfterLanding points past landing" in {
        val cfg    = configWith(keepBefore = 50, keepAfter = 3)
        val points =
          annotatedStream(
            50,
            takeoff = Some(eventAt(5)),
            freefall = Some(eventAt(10)),
            landing = Some(eventAt(30))
          )
        val result = runCutter(cfg, points)
        result.last.source shouldBe 33
      }

      "should emit zero points after landing when keepAfterLanding is 0" in {
        val cfg    = configWith(keepBefore = 50, keepAfter = 0)
        val points =
          annotatedStream(
            50,
            takeoff = Some(eventAt(5)),
            freefall = Some(eventAt(10)),
            landing = Some(eventAt(30))
          )
        val result = runCutter(cfg, points)
        result.count(_.source > 30) shouldBe 0
      }

      "should work with keepAfterLanding = 1" in {
        val cfg    = configWith(keepBefore = 50, keepAfter = 1)
        val points =
          annotatedStream(
            50,
            takeoff = Some(eventAt(5)),
            freefall = Some(eventAt(10)),
            landing = Some(eventAt(30))
          )
        val result = runCutter(cfg, points)
        result.count(_.source > 30) shouldBe 1
      }

      "should keep all remaining points when keepAfterLanding exceeds available points" in {
        val cfg    = configWith(keepBefore = 50, keepAfter = 100)
        val points =
          annotatedStream(
            50,
            takeoff = Some(eventAt(5)),
            freefall = Some(eventAt(10)),
            landing = Some(eventAt(45))
          )
        val result = runCutter(cfg, points)
        result.last.source shouldBe 49
        result.count(_.source > 45) shouldBe 4
      }

      "should include the landing point itself in the output" in {
        val cfg    = configWith(keepBefore = 50, keepAfter = 3)
        val points =
          annotatedStream(
            50,
            takeoff = Some(eventAt(5)),
            freefall = Some(eventAt(10)),
            landing = Some(eventAt(30))
          )
        val result = runCutter(cfg, points)
        sources(result) should contain(30)
      }

      "should handle landing at the last stream index" in {
        val cfg    = configWith(keepBefore = 50, keepAfter = 5)
        val points =
          annotatedStream(
            30,
            takeoff = Some(eventAt(5)),
            freefall = Some(eventAt(10)),
            landing = Some(eventAt(29))
          )
        val result = runCutter(cfg, points)
        result.last.source shouldBe 29
        result.count(_.source > 29) shouldBe 0
      }

    }

    "retaining all final points when no landing is detected" - {

      "should emit all points from takeoff window to end when landing is None" in {
        val cfg    = configWith(keepBefore = 3, keepAfter = 5)
        val points = annotatedStream(30, takeoff = Some(eventAt(10)), freefall = Some(eventAt(15)), landing = None)
        val result = runCutter(cfg, points)
        result.head.source shouldBe 7
        result.last.source shouldBe 29
        result.size shouldBe (30 - 7)
      }

      "should emit all points to end regardless of keepAfterLanding when no landing" in {
        val cfg    = configWith(keepBefore = 50, keepAfter = 1)
        val points = annotatedStream(50, takeoff = Some(eventAt(5)), freefall = Some(eventAt(10)), landing = None)
        val result = runCutter(cfg, points)
        result.last.source shouldBe 49
      }

    }

    "minimum retained points percentage" - {

      "should retain all points when cutting drops below minRetainedPoints percentage" in {
        val cfg    = configWith(keepBefore = 2, keepAfter = 2, minRetained = 0.5)
        val points =
          annotatedStream(
            100,
            takeoff = Some(eventAt(90)),
            freefall = Some(eventAt(92)),
            landing = Some(eventAt(95))
          )
        val result = runCutter(cfg, points)
        result.size shouldBe 100
      }

      "should cut normally when retained percentage exceeds minRetainedPoints" in {
        val cfg    = configWith(keepBefore = 5, keepAfter = 5, minRetained = 0.1)
        val points =
          annotatedStream(
            100,
            takeoff = Some(eventAt(10)),
            freefall = Some(eventAt(20)),
            landing = Some(eventAt(90))
          )
        val result = runCutter(cfg, points)
        result.size should be < 100
      }

      "should retain all points when minRetainedPoints is 1.0" in {
        val cfg    = configWith(keepBefore = 5, keepAfter = 5, minRetained = 1.0)
        val points =
          annotatedStream(
            50,
            takeoff = Some(eventAt(20)),
            freefall = Some(eventAt(25)),
            landing = Some(eventAt(40))
          )
        val result = runCutter(cfg, points)
        result.size shouldBe 50
      }

      "should always cut when minRetainedPoints is 0.0" in {
        val cfg    = configWith(keepBefore = 2, keepAfter = 2, minRetained = 0.0)
        val points =
          annotatedStream(
            50,
            takeoff = Some(eventAt(20)),
            freefall = Some(eventAt(25)),
            landing = Some(eventAt(40))
          )
        val result = runCutter(cfg, points)
        result.size should be < 50
      }

    }

    "cutting both before takeoff and after landing" - {

      "should cut both ends of the stream" in {
        val cfg    = configWith(keepBefore = 3, keepAfter = 4)
        val points =
          annotatedStream(
            50,
            takeoff = Some(eventAt(15)),
            freefall = Some(eventAt(20)),
            landing = Some(eventAt(35))
          )
        val result = runCutter(cfg, points)
        result.head.source shouldBe 12
        result.last.source shouldBe 39
        result.size shouldBe (39 - 12 + 1)
      }

      "should produce contiguous source indices with no gaps" in {
        val cfg    = configWith(keepBefore = 3, keepAfter = 3)
        val points =
          annotatedStream(
            50,
            takeoff = Some(eventAt(15)),
            freefall = Some(eventAt(20)),
            landing = Some(eventAt(40))
          )
        val result = runCutter(cfg, points)
        val s      = sources(result)
        s shouldBe (s.head to s.last).toVector
      }

      "should preserve point ordering" in {
        val cfg    = configWith(keepBefore = 5, keepAfter = 5)
        val points =
          annotatedStream(
            50,
            takeoff = Some(eventAt(15)),
            freefall = Some(eventAt(20)),
            landing = Some(eventAt(40))
          )
        val result = runCutter(cfg, points)
        val s      = sources(result)
        s shouldBe s.sorted
      }

      "should always include all in-flight points (takeoff to landing)" in {
        val cfg    = configWith(keepBefore = 2, keepAfter = 2)
        val points =
          annotatedStream(
            50,
            takeoff = Some(eventAt(15)),
            freefall = Some(eventAt(20)),
            landing = Some(eventAt(40))
          )
        val result = runCutter(cfg, points)
        val s      = sources(result)
        (15 to 40).foreach(i => s should contain(i))
      }

    }

    "handling empty streams" - {

      "should produce no output for an empty stream" in {
        val cfg    = configWith()
        val result = runCutter(cfg, Vector.empty)
        result shouldBe empty
      }

    }

    "handling single-element streams" - {

      "should emit the single point when no events are detected" in {
        val cfg    = configWith()
        val points = annotatedStream(1)
        val result = runCutter(cfg, points)
        result.size shouldBe 1
      }

      "should emit the single point when takeoff and landing are detected" in {
        val cfg    = configWith()
        val points =
          annotatedStream(1, takeoff = Some(eventAt(0)), freefall = Some(eventAt(0)), landing = Some(eventAt(0)))
        val result = runCutter(cfg, points)
        result.size shouldBe 1
      }

    }

    "handling two-element streams" - {

      "should emit both points when no takeoff is detected" in {
        val cfg    = configWith(keepBefore = 5, keepAfter = 5)
        val points = annotatedStream(2, freefall = Some(eventAt(0)))
        val result = runCutter(cfg, points)
        result.size shouldBe 2
      }

      "should emit both points when no events are detected" in {
        val cfg    = configWith()
        val points = annotatedStream(2)
        val result = runCutter(cfg, points)
        result.size shouldBe 2
      }

    }

    "preserving source data through the pipe" - {

      "should preserve the original source values on emitted points" in {
        val cfg    = configWith(keepBefore = 2, keepAfter = 2)
        val points =
          annotatedStream(
            30,
            takeoff = Some(eventAt(10)),
            freefall = Some(eventAt(15)),
            landing = Some(eventAt(25))
          )
        val result = runCutter(cfg, points)
        result.foreach(p => p.source shouldBe sources(result)(result.indexOf(p)))
      }

      "should preserve event annotations on emitted points" in {
        val takeoff  = Some(eventAt(10))
        val freefall = Some(eventAt(15))
        val landing  = Some(eventAt(25))
        val cfg      = configWith(keepBefore = 2, keepAfter = 2)
        val points   = annotatedStream(30, takeoff = takeoff, freefall = freefall, landing = landing)
        val result   = runCutter(cfg, points)
        result.foreach { p =>
          p.takeoff shouldBe takeoff
          p.freefall shouldBe freefall
          p.landing shouldBe landing
        }
      }

    }

    "handling takeoff and landing close together" - {

      "should handle adjacent takeoff and landing (landing = takeoff + 1)" in {
        val cfg    = configWith(keepBefore = 2, keepAfter = 2)
        val points =
          annotatedStream(
            20,
            takeoff = Some(eventAt(8)),
            freefall = Some(eventAt(9)),
            landing = Some(eventAt(9))
          )
        val result = runCutter(cfg, points)
        sources(result) should contain(8)
        sources(result) should contain(9)
      }

      "should handle same takeoff and landing index" in {
        val cfg    = configWith(keepBefore = 2, keepAfter = 2)
        val points =
          annotatedStream(
            20,
            takeoff = Some(eventAt(10)),
            freefall = Some(eventAt(10)),
            landing = Some(eventAt(10))
          )
        val result = runCutter(cfg, points)
        sources(result) should contain(10)
      }

      "should handle overlapping keepBefore and keepAfter windows" in {
        val cfg    = configWith(keepBefore = 3, keepAfter = 3)
        val points =
          annotatedStream(
            20,
            takeoff = Some(eventAt(5)),
            freefall = Some(eventAt(6)),
            landing = Some(eventAt(8))
          )
        val result = runCutter(cfg, points)
        result.head.source shouldBe 2
        result.last.source shouldBe 11
      }

    }

    "handling takeoff near end of stream" - {

      "should handle takeoff at the last index" in {
        val cfg    = configWith(keepBefore = 3, keepAfter = 5)
        val points = annotatedStream(20, takeoff = Some(eventAt(19)), freefall = Some(eventAt(19)), landing = None)
        val result = runCutter(cfg, points)
        result.head.source shouldBe 16
        result.last.source shouldBe 19
      }

      "should handle takeoff at second-to-last index" in {
        val cfg    = configWith(keepBefore = 2, keepAfter = 50)
        val points = annotatedStream(20, takeoff = Some(eventAt(18)), freefall = Some(eventAt(18)), landing = None)
        val result = runCutter(cfg, points)
        result.head.source shouldBe 16
        result.last.source shouldBe 19
      }

    }

  }

  private def configWith(
    keepBefore: Int = 5,
    keepAfter: Int = 5,
    minRetained: Double = 0.0,
  ): Config =
    Config.default.copy(
      dataCutterConfig = DataCutterConfig(
        keepBeforeTakeoff = keepBefore,
        keepAfterLanding = keepAfter,
        minRetainedPoints = minRetained,
      ),
    )

  private def eventAt(idx: Long): FlightEventPoint =
    FlightEventPoint(index = idx, altitude = 1000.0)

  private def annotatedStream(
    n: Int,
    takeoff: Option[FlightEventPoint] = None,
    freefall: Option[FlightEventPoint] = None,
    canopy: Option[FlightEventPoint] = None,
    landing: Option[FlightEventPoint] = None,
  ): Vector[OutputFlightPoint[Int]] = (0 until n).map { i =>
    OutputFlightPoint[Int](
      takeoff = takeoff,
      freefall = freefall,
      canopy = canopy,
      landing = landing,
      source = i,
    )
  }.toVector

  private def runCutter(
    config: Config,
    points: Vector[OutputFlightPoint[Int]],
  ): Vector[OutputFlightPoint[Int]] =
    val cutter = DataCutter(config)
    fs2.Stream
      .emits[IO, OutputFlightPoint[Int]](points)
      .through(cutter.cutPipe[IO, Int])
      .compile
      .toVector
      .unsafeRunSync()

  private def sources(points: Vector[OutputFlightPoint[Int]]): Vector[Int] =
    points.map(_.source)

}
