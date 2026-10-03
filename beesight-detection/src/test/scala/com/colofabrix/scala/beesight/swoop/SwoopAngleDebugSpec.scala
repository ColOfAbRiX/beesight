package com.colofabrix.scala.beesight.swoop

import cats.effect.IO
import cats.syntax.all.given
import com.colofabrix.scala.beesight.*
import com.colofabrix.scala.beesight.TestFilesHandler.given
import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.jumpevents.JumpEventsDetector
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.swoop.computations.{ SwoopCascade, SwoopGround, SwoopMetrics }
import com.colofabrix.scala.beesight.swoop.model.*
import java.nio.file.Paths
import org.scalatest.wordspec.AnyWordSpec
import scala.concurrent.duration.*

/**
 * Diagnostic spec: dumps the swoop detection internals for files whose detected turn angle disagrees with the
 * logbook. Not an assertion suite - it prints data for manual analysis.
 */
class SwoopAngleDebugSpec extends AnyWordSpec with IOValues {

  private val testFilesDir = Paths.get("src/test/resources/test_files")

  /** Representative failing files: over-count files broken by the carve tail, plus under-reported contrasts. */
  private val files = List(
    "csv_files/jump_961.zip",  // expected  90, over-count
    "csv_files/jump_963.zip",  // expected  45, under-reported tail excluded by user verdict
    "csv_files/jump_965.zip",  // expected  90, under-reported decay tail
    "csv_files/jump_1133.zip", // expected  90, over-count
    "csv_files/jump_1422.zip", // expected  90, over-count
    "csv_files/jump_1466.zip", // expected  90, over-count
    "csv_files/jump_973.zip",  // expected  90, over-count
  )

  "Swoop angle debug" should {

    "dump turn-window details for failing files" in {
      val output = files
        .parFlatTraverse { file =>
          debugFile(testFilesDir.resolve(file)).map(lines => List(s"\n=== $file ===") ++ lines)
        }
        .result(timeout = 5.minutes)

      val outPath = java.nio.file.Paths.get("target", "swoop-angle-debug.txt")
      java.nio.file.Files.createDirectories(outPath.getParent)
      java.nio.file.Files.write(outPath, output.mkString("\n").getBytes("UTF-8"))

      info(s"Written diagnostics to $outPath")
    }

  }

  private def debugFile(filePath: java.nio.file.Path): IO[List[String]] =
    for {
      events <- detectEvents(filePath)
      points <- TestFilesHandler.readCsv[CsvPoint](filePath).compile.toVector
    } yield {
      val series = PointSeries(points.zipWithIndex.map { case (p, i) => IndexedPoint.fromZip(p, i.toLong) })

      val ground       = SwoopGround(DetectionConfig.default.swoop).detect(series, events.landing)
      val patternEntry = SwoopCascade.findPatternEntry(series, ground)
      val dive         = SwoopCascade.findDivePoint(series, patternEntry.getOrElse(0), ground)
      val topOfTurn    = dive.map(d =>
        SwoopCascade.findTopOfTurn(
          series,
          d,
          patternEntry.getOrElse(0),
          DetectionConfig.default.swoop.pullThreshold.value
        )
      )
      val stop    = dive.flatMap(d => SwoopCascade.findStop(series, d))
      val rollout = for {
        d <- dive
        t <- topOfTurn
        s <- stop
      } yield SwoopCascade.findRolloutStart(
        series,
        t,
        s,
        DetectionConfig.default.swoop.rolloutWindow.value
      ).getOrElse(-1)
      val bottom = for {
        r <- rollout
        s <- stop
      } yield SwoopCascade.findBottomOfTurn(series, r, s)

      val header = List(
        f"ground=$ground%.1f  patternEntry=$patternEntry  dive=$dive  topOfTurn=$topOfTurn  rollout=$rollout  bottom=$bottom  stop=$stop"
      )

      val turnDegrees = for {
        d <- dive
        t <- topOfTurn
        b <- bottom
      } yield SwoopMetrics.turnDegrees(series, t, stop.getOrElse(b), d)

      val summary = List(
        f"turnDegrees=${turnDegrees.getOrElse("n/a")}",
        f"events: landing=${events.landing.isDefined} canopy=${events.canopy.isDefined}"
      )

      //  Per-sample dump: 10s before topOfTurn through 40s after stop, to cover the full rollout and landing  //
      val from = topOfTurn.getOrElse(0) - 50 max 0
      val to   = (stop.getOrElse(bottom.getOrElse(series.length - 1)) + 200) min (series.length - 1)
      val agl  = (i: Int) => series.altitude(i) - ground

      def wrap(raw: Double): Double =
        if raw > 180.0 then raw - 360.0 else if raw < -180.0 then raw + 360.0 else raw

      def clock(millis: Double): String = {
        val t = java.time.Instant.ofEpochMilli(millis.toLong).atZone(java.time.ZoneOffset.UTC)
        f"${t.getHour}%02d:${t.getMinute}%02d:${t.getSecond}%02d"
      }

      val rows = (from to to).map { i =>
        val rawDelta  = if i == 0 then 0.0 else wrap(series.heading(i) - series.heading(i - 1))
        val diveAngle =
          val h = series.horizontalSpeed(i)
          val v = series.verticalSpeed(i)
          if h == 0.0 then (if v < 0 then -90.0 else 90.0) else Math.toDegrees(Math.atan(v / h))
        val hs = series.horizontalSpeed(i)
        val vz = series.verticalSpeed(i)
        val total = Math.sqrt(hs * hs + vz * vz)
        f"  i=$i%5d  t=${(series.timeMillis(i) - series.timeMillis(from)) / 1000.0}%7.2f  clock=${clock(series.timeMillis(i))}  msl=${series.altitude(i)}%7.1f  agl=${agl(i)}%7.1f  vz=$vz%7.2f  hs=$hs%6.2f  total=$total%6.2f  hdg=${series.heading(i)}%6.1f  dHdg=$rawDelta%7.1f  diveAng=$diveAngle%6.1f"
      }

      header ++ summary ++ rows
    }

  private def detectEvents(filePath: java.nio.file.Path): IO[FlightEvents] =
    TestFilesHandler
      .readCsv[CsvPoint](filePath)
      .through(JumpEventsDetector(DetectionConfig.default.jumpevents).detect)
      .compile
      .last
      .map(_.getOrElse(FlightEvents.empty))

}
