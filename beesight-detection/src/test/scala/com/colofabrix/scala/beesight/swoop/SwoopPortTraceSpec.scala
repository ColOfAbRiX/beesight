package com.colofabrix.scala.beesight.swoop

import cats.effect.IO
import com.colofabrix.scala.beesight.*
import com.colofabrix.scala.beesight.TestFilesHandler.given
import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.jumpevents.JumpEventsDetector
import com.colofabrix.scala.beesight.math.Heading
import com.colofabrix.scala.beesight.model.*
import java.nio.file.Paths
import org.scalatest.wordspec.AnyWordSpec
import scala.concurrent.duration.*

/** TEMPORARY trace spec for the gSwoop port validation - prints the detection cascade per file. Delete after use. */
class SwoopPortTraceSpec extends AnyWordSpec with IOValues {

  private val testFilesDir = Paths.get("src/test/resources/test_files")
  private val resultsFile  = Paths.get("src/test/resources/test_files/swoop_results.csv")
  private val config       = DetectionConfig.default

  private val traceFiles = List(
    "jump_795.zip",  // offset failure: turnInit +122, rollout +265
    "jump_794.zip",  // offset failure: rollout +182, degrees +178
    "jump_797.zip",  // offset failure: turnInit +244, rollout +196
    "jump_800.zip",  // degrees failure: expected 47.0, got -46.9
    "jump_1463.zip", // degrees failure: expected 83, got 557.9
    "jump_1933.zip", // false positive: gSwoop says None
    "jump_1935.zip", // false positive: gSwoop says None
    "jump_813.zip",  // control: currently MATCHES
    "jump_1403.zip", // control: currently MATCHES
  )

  "SwoopDataDetector trace" should {

    "trace the detection cascade on representative files" in {
      val expectedValues =
        TestFilesHandler
          .readCsv[GSwoopResult](resultsFile)
          .compile
          .toList
          .result(timeout = 1.minute)
          .map(row => row.filename -> row)
          .toMap

      traceFiles.foreach { name =>
        val filename = s"csv_files/$name"
        val path     = testFilesDir.resolve(filename)

        val program =
          for
            events <- IO.defer(detectEvents(path))
            trace  <- SwoopDataDetector(config).detectWithTrace(events, TestFilesHandler.readCsv[DataPoint](path))
          yield (expectedValues.get(filename), trace)

        val (expected, trace) = program.result(timeout = 2.minutes)

        println(s"\n===== $name =====")
        println(f"  points=${trace.points.length}  ground(last point)=${trace.ground}%.1f m")

        val agl = (i: Int) => trace.points(i).point.altitude - trace.ground

        def stage(label: String, idx: Option[Int]): Unit =
          println(idx match
            case Some(i) => f"  $label%-14s idx=$i%5d  agl=${agl(i)}%8.1f m  msl=${trace.points(i).point.altitude}%8.1f m"
            case None    => f"  $label%-14s idx=  n/a"
          )

        stage("patternEntry", trace.patternEntry)
        stage("dive", trace.dive)
        stage("topOfTurn", trace.topOfTurn)
        stage("stop", trace.stop)
        stage("rollout", trace.rollout)
        stage("bottom", trace.bottom)

        trace.result match
          case Some(s) =>
            val degRaw = (trace.topOfTurn, trace.bottom) match
              case (Some(t), Some(b)) =>
                rawDegrees(trace.points, t, Math.min(b, trace.points.length - 1))
              case _ => Double.NaN
            println(
              f"  result: turnInit=${s.turnInitiation.altitude}%.1f rollout=${s.rolloutStart.altitude}%.1f " +
                f"maxVz=${s.maxVerticalSpeed.speed}%.2f maxTot=${s.maxTotalSpeed.speed}%.2f maxHorz=${s.maxHorizontalSpeed.speed}%.2f " +
                f"deg=${s.degreesOfRotation}%.1f degRaw=$degRaw%.1f tExec=${s.timeToExecuteTurn.toMillis / 1000.0}%.1f s"
            )
          case None =>
            println("  result: None")

        expected match
          case Some(e) if !e.isEmpty =>
            println(
              f"  expect: turnInit=${e.turnInitiationAltitude.getOrElse(Double.NaN)}%.1f rollout=${e.rolloutStartAltitude.getOrElse(Double.NaN)}%.1f " +
                f"maxVz=${e.maxVerticalSpeed.getOrElse(Double.NaN)}%.2f maxTot=${e.maxTotalSpeed.getOrElse(Double.NaN)}%.2f " +
                f"maxHorz=${e.maxHorizontalSpeed.getOrElse(Double.NaN)}%.2f deg=${e.degreesOfRotation.getOrElse(Double.NaN)}%.1f " +
                f"tExec=${e.timeToExecuteTurn.getOrElse(Double.NaN)}%.1f s"
            )
          case Some(_) =>
            println("  expect: NO SWOOP (gSwoop reported nothing)")
          case None =>
            println("  expect: <no row in swoop_results.csv>")
      }

      succeed
    }

  }

  //  Pipeline helpers (same as the bulk spec)  //

  /** The pre-unwrap degrees: signed sum of raw wrapped heading deltas over (top of turn, bottom of turn]. */
  private def rawDegrees(points: Vector[IndexedPoint], topOfTurn: Int, bottomIdx: Int): Double =
    var total    = 0.0
    var previous = Heading.headingDegrees(points(topOfTurn).point.speed)

    for i <- topOfTurn + 1 to bottomIdx do
      val heading = Heading.headingDegrees(points(i).point.speed)
      val delta0  = heading - previous
      val delta   = if delta0 > 180.0 then delta0 - 360.0 else if delta0 < -180.0 then delta0 + 360.0 else delta0
      if delta < 360.0 then total += delta
      previous = heading

    total

  private def detectEvents(path: java.nio.file.Path): IO[FlightEvents] =
    TestFilesHandler
      .readCsv[DataPoint](path)
      .through(JumpEventsDetector(config).detectEvents)
      .compile
      .last
      .map(_.getOrElse(FlightEvents.empty))

}
