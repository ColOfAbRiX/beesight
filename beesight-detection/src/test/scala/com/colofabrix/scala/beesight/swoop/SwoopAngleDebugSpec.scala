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

  /** Representative failing files: real turns the carve under-reports by 30-50 degrees. */
  private val files = List(
    "csv_files/jump_962.zip",  // expected  90, detected  33.4
    "csv_files/jump_963.zip",  // expected  90, detected  44.1
    "csv_files/jump_965.zip",  // expected  90, detected  59.0
    "csv_files/jump_967.zip",  // expected  90, detected  49.1
    "csv_files/jump_968.zip",  // expected  90, detected  48.7
    "csv_files/jump_1418.zip", // expected  90, detected  58.6
    "csv_files/jump_1445.zip", // expected  90, detected  43.2
    "csv_files/jump_1857.zip", // expected 135, detected  88.1
    //  passing carves for contrast  //
    "csv_files/jump_1155.zip", // expected  90, detected  86.4
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

      val ground       = SwoopGround.detect(series, events.landing)
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
      } yield SwoopMetrics.turnDegrees(series, t, b, d)

      val summary = List(
        f"turnDegrees=${turnDegrees.getOrElse("n/a")}",
        f"events: landing=${events.landing.isDefined} canopy=${events.canopy.isDefined}"
      )

      //  Per-sample dump over the turn window (with a little margin)  //
      val from = topOfTurn.getOrElse(0) - 3 max 0
      val to   = (bottom.getOrElse(series.length - 1) + 3) min (series.length - 1)
      val agl  = (i: Int) => series.altitude(i) - ground

      def wrap(raw: Double): Double =
        if raw > 180.0 then raw - 360.0 else if raw < -180.0 then raw + 360.0 else raw

      val rows = (from to to).map { i =>
        val rawDelta  = if i == 0 then 0.0 else wrap(series.heading(i) - series.heading(i - 1))
        val diveAngle =
          val h = series.horizontalSpeed(i)
          val v = series.verticalSpeed(i)
          if h == 0.0 then (if v < 0 then -90.0 else 90.0) else Math.toDegrees(Math.atan(v / h))
        f"  i=$i%5d  t=${(series.timeMillis(i) - series.timeMillis(from)) / 1000.0}%7.2f  agl=${agl(i)}%7.1f  vz=${series.verticalSpeed(i)}%7.2f  hs=${series.horizontalSpeed(i)}%6.2f  hdg=${series.heading(i)}%6.1f  dHdg=$rawDelta%7.1f  diveAng=$diveAngle%6.1f"
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
