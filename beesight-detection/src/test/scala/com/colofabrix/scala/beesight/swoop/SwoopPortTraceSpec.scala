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
    "jump_807.zip", // unclassified: canopy turn spike near landing (813-twin mystery)
    "jump_808.zip", // user-confirmed: wingsuit, NO swoop - genuine port false positive
    "jump_1859.zip", // user-confirmed: 135 deg swoop - gSwoop baseline missed it?
    "jump_1935.zip", // user-confirmed: 135 deg swoop - gSwoop baseline missed it?
    "jump_813.zip",  // TRUE positive reference (port matches gSwoop exactly)
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
          yield (expectedValues.get(filename), trace, events)

        val (expected, trace, events) = program.result(timeout = 2.minutes)

        println(s"\n===== $name =====")
        println(f"  points=${trace.points.length}  ground(last point)=${trace.ground}%.1f m")

        def evStage(label: String, ep: Option[EventPoint]): Unit =
          println(ep match
            case Some(e) => f"  event $label%-8s idx=${e.index}%5d  alt=${e.altitude}%8.1f m"
            case None    => f"  event $label%-8s n/a"
          )

        evStage("takeoff", events.takeoff)
        evStage("freefall", events.freefall)
        evStage("canopy", events.canopy)
        evStage("landing", events.landing)

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

        // TEMPORARY: vz profile for the freefall-signature fallback
        val vzs        = trace.points.map(_.point.speed.vertical)
        val thresholds = List(70.0, 60.0, 50.0, 40.0, 30.0)
        def longestRun(thresholdMps: Double): Double =
          var best     = 0.0
          var runStart = -1
          var i        = 0
          while i < trace.points.length do
            val p = trace.points(i).point
            if p.speed.vertical > thresholdMps then
              if runStart < 0 then runStart = i
              best = best.max((p.time.toEpochMilli - trace.points(runStart).point.time.toEpochMilli) / 1000.0)
            else runStart = -1
            i += 1
          best
        val maxVzIdx = vzs.indexOf(vzs.max)

        // az = backward-difference vertical acceleration (m/s^2); gSwoop's freefall test is -az > 12 mph/s
        val minAz = (0 until trace.points.length - 1)
          .map { i =>
            val dtSec =
              (trace.points(i + 1).point.time.toEpochMilli - trace.points(i).point.time.toEpochMilli) / 1000.0
            if dtSec > 0 then (vzs(i + 1) - vzs(i)) / dtSec else 0.0
          }
          .min
        def longestAzRun: Double =
          var best     = 0.0
          var runStart = -1
          var i        = 0
          while i < trace.points.length - 1 do
            val dt = (trace.points(i + 1).point.time.toEpochMilli - trace.points(i).point.time.toEpochMilli) / 1000.0
            val az = if dt > 0 then (vzs(i + 1) - vzs(i)) / dt else 0.0
            if -az * 2.2369362920544 > 12.0 then
              if runStart < 0 then runStart = i
              best = best.max(trace.points(i).point.time.toEpochMilli.toDouble - trace.points(runStart).point.time.toEpochMilli.toDouble) / 1000.0
            else runStart = -1
            i += 1
          best
        // Altitude-derived descent rate: dz/dt from consecutive altitudes (robust to zeroed velocity fields)
        val altVz = (0 until trace.points.length - 1).map { i =>
          val dtSec =
            (trace.points(i + 1).point.time.toEpochMilli - trace.points(i).point.time.toEpochMilli) / 1000.0
          if dtSec > 0 then (trace.points(i).point.altitude - trace.points(i + 1).point.altitude) / dtSec else 0.0
        }.toVector
        def longestAltRun(thresholdMps: Double): Double =
          var best     = 0.0
          var runStart = -1
          var i        = 0
          while i < altVz.length do
            if altVz(i) > thresholdMps then
              if runStart < 0 then runStart = i
              best = best.max((trace.points(i + 1).point.time.toEpochMilli - trace.points(runStart + 1).point.time.toEpochMilli) / 1000.0)
            else runStart = -1
            i += 1
          best
        println(
          f"  vz profile: firstVz=${vzs.head}%.1f  maxVz=${vzs.max}%.1f m/s @idx=$maxVzIdx%d  " +
            thresholds.map(mph => f">${mph.toInt}mph=${longestRun(mph / 2.2369362920544)}%.1fs").mkString("  ") +
            f"  minAz=$minAz%.1f m/s2  >12mph/s-az=${longestAzRun}%.1fs"
        )
        println(
          "  alt-vz profile: " +
            thresholds.map(mph => f">${mph.toInt}mph=${longestAltRun(mph / 2.2369362920544)}%.1fs").mkString("  ") +
            f"  maxAltVz=${if altVz.isEmpty then 0.0 else altVz.max}%.1f m/s"
        )

        // TEMPORARY: sampled profile over [rollout, stop] - shows what the rollout walk locked onto
        (trace.rollout, trace.stop) match
          case (Some(r), Some(s)) if r <= s =>
            println("  [rollout..stop] profile (t rel. rollout s | agl m | vz m/s | horz m/s):")
            val t0   = trace.points(r).point.time.toEpochMilli
            var next = 0.0
            for i <- r to s do
              val tSec = (trace.points(i).point.time.toEpochMilli - t0) / 1000.0
              if tSec >= next then
                val p = trace.points(i).point
                println(f"    t=${tSec}%5.1f  agl=${agl(i)}%7.1f  vz=${p.speed.vertical}%6.2f  horz=${p.speed.horizontal}%6.2f")
                next += 1.0
          case _ => ()
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
