package com.colofabrix.scala.beesight.swoop

import cats.effect.IO
import cats.syntax.all.given
import com.colofabrix.scala.beesight.*
import com.colofabrix.scala.beesight.TestFilesHandler.given
import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.jumpevents.JumpEventsDetector
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.swoop.model.*
import java.nio.file.Paths
import org.scalatest.wordspec.AnyWordSpec
import scala.concurrent.duration.*

/**
 * FIXME: This file is temporary, remove when analysis and fix is done.
 *
 * Speed-floor sweep for the carve turn angle. Hypothesis: in the expected-0 recordings the rotation seen by the
 * detector is the turn into final, flown at low speed before the swoop - the swoop itself is straight and speed
 * builds only after the turn. If so, raising the reliable-speed floor should exclude the approach turn while
 * keeping the swoop's own rotation. Sweeps the floor from 4 to 15 m/s (tail and core rate fixed at 20 deg/s) and
 * reports, per recording, every variant's value plus the baseline carve window with the horizontal speed at its
 * start, at the dive point, at its end and over the whole window.
 */
class SwoopAngleExperimentSpec extends AnyWordSpec with IOValues {

  private val testFilesDir = Paths.get("src/test/resources/test_files")
  private val resultsFile  = Paths.get("src/test/resources/test_files/swoop_results.csv")

  private val tolerance = 30.0
  private val floors    = List(4.0, 6.0, 8.0, 10.0, 12.0, 15.0)

  "Swoop angle experiment" should {

    "sweep the carve reliable-speed floor against the logbook" in {
      val expectedValues =
        TestFilesHandler.readCsv[GSwoopResult](resultsFile).compile.toList.result()

      val rows = expectedValues
        .parFlatTraverse { expected =>
          val filePath = testFilesDir.resolve(expected.filename)
          evaluateFile(filePath, expected).map {
            case Some(line) => List(line)
            case None       => Nil
          }
        }
        .result(timeout = 15.minutes)

      val outPath = java.nio.file.Paths.get("target", "swoop-angle-speed-floor.txt")
      java.nio.file.Files.createDirectories(outPath.getParent)

      val header = "filename,expected," + floors.map(f => f"hs$f%.0f").mkString(",") +
        ",start,end,dive,hsStart,hsDive,hsEnd,hsMin,hsMax"
      val summary = summarize(rows)

      val content = header :: rows ::: List("", summary)
      java.nio.file.Files.write(outPath, content.mkString("\n").getBytes("UTF-8"))

      info(s"Written comparison to $outPath\n$summary")
    }

  }

  /** One line per file: the expected angle, every floor variant's value, and the baseline carve window diagnostics. */
  private def evaluateFile(filePath: java.nio.file.Path, expected: GSwoopResult): IO[Option[String]] =
    if expected.expectedAngle.isEmpty then IO.pure(None)
    else
      for {
        events <- detectEvents(filePath)
        points <- TestFilesHandler.readCsv[CsvPoint](filePath).compile.toVector
      } yield {
        val series = PointSeries(points.zipWithIndex.map { case (p, i) => IndexedPoint.fromZip(p, i.toLong) })

        val ground       = SwoopGround.detect(series, events.landing)
        val patternEntry = SwoopCascade.findPatternEntry(series, ground)
        val diveOpt      = SwoopCascade.findDivePoint(series, patternEntry.getOrElse(0), ground)

        diveOpt.flatMap { dive =>
          val pe         = patternEntry.getOrElse(0)
          val top        = SwoopCascade.findTopOfTurn(series, dive, pe, DetectionConfig.default.swoop.pullThreshold)
          val stopOpt    = SwoopCascade.findStop(series, dive)
          val rolloutOpt = stopOpt.flatMap { stop =>
            SwoopCascade.findRolloutStart(series, top, stop, DetectionConfig.default.swoop.rolloutWindow)
          }
          val bottomOpt = for {
            r <- rolloutOpt
            s <- stopOpt
          } yield SwoopCascade.findBottomOfTurn(series, r, s)

          stopOpt.flatMap { stop =>
            rolloutOpt.flatMap { _ =>
              val bottom = bottomOpt.getOrElse(stop)

              val results = floors.map(f =>
                rateCarve(series, top, bottom, dive, coreRate = 20.0, tailRate = 20.0, minHs = f, gap = 5)
              )
              val base = results.head

              val hsMin = (base.start to base.end).map(series.horizontalSpeed).min
              val hsMax = (base.start to base.end).map(series.horizontalSpeed).max

              results.map(r => f"${r.value}%.1f").mkString(",")
              val diag =
                f"${base.start},${base.end},$dive,${series.horizontalSpeed(base.start)}%.1f,${series.horizontalSpeed(dive)}%.1f,${series.horizontalSpeed(base.end)}%.1f,$hsMin%.1f,$hsMax%.1f"

              val exp = expected.expectedAngle.getOrElse(Double.NaN)
              Some(f"${expected.filename},$exp%.0f,${results.map(_.value).map(v => f"$v%.1f").mkString(",")},$diag")
            }
          }
        }
      }

  /** Pass-rate summary per floor variant, plus fixed/broken file lists relative to the 4 m/s baseline. */
  private def summarize(rows: List[String]): String = {
    val parsed = rows.map(_.split(",").toList)
    val names  = floors.map(f => f"hs$f%.0f")

    def passes(col: Int): List[Boolean] =
      parsed.map(p => Math.abs(Math.abs(p(col).toDouble) - Math.abs(p(1).toDouble)) <= tolerance)

    val counts = names.zipWithIndex.map { (name, i) =>
      val n = passes(i + 2).count(identity)
      f"$name%-6s: $n%3d / ${parsed.size}%3d  (${100.0 * n / parsed.size}%.1f%%)"
    }

    val base  = passes(2)
    val diffs = names.zipWithIndex.tail.map { (name, i) =>
      val flags  = passes(i + 2)
      val fixed  = parsed.zip(flags).zip(base).collect { case ((p, f), b) if f && !b => p(0) }
      val broken = parsed.zip(flags).zip(base).collect { case ((p, f), b) if !f && b => p(0) }
      s"$name vs hs4: fixes ${fixed.size} [${fixed.mkString(", ")}], breaks ${broken.size} [${broken.mkString(", ")}]"
    }

    (f"Total files: ${parsed.size}" :: counts ::: List("") ::: diffs).mkString("\n")
  }

  //  The validated carve formula, parameterised  //

  /** The carve turn angle plus the window it was measured over. */
  final case class CarveResult(value: Double, start: Int, end: Int)

  /**
   * The carve turn angle: the net heading change over the maximal interval around the dive point where the smoothed
   * turn rate stays above the tail rate in the dominant direction, considering only samples whose horizontal speed
   * makes the GPS heading reliable and tolerating brief gaps.
   */
  private def rateCarve(
    series: PointSeries,
    top: Int,
    bottom: Int,
    dive: Int,
    coreRate: Double,
    tailRate: Double,
    minHs: Double,
    gap: Int
  ): CarveResult = {
    val n = series.length

    //  Per-sample turn rate (deg/s), only where both samples are reliable  //
    def rate(i: Int): Option[Double] =
      if i <= 0 || i >= n then None
      else {
        val dtSec = (series.timeMillis(i) - series.timeMillis(i - 1)) / 1000.0
        if dtSec <= 0 then None
        else if series.horizontalSpeed(i) < minHs || series.horizontalSpeed(i - 1) < minHs then None
        else Some(wrap(series.heading(i) - series.heading(i - 1)) / dtSec)
      }

    //  Smoothed rate over a +/-2 sample window  //
    def smoothRate(i: Int): Option[Double] = {
      val values = (i - 2 to i + 2).flatMap(rate)
      if values.isEmpty then None else Some(values.sum / values.size)
    }

    //  Dominant direction: the net heading change around the dive point over +/-2 s of reliable samples  //
    val backRef   = (dive - 10 to top by -1).find(i => series.horizontalSpeed(i) >= minHs).getOrElse(dive - 10 max top)
    val fwdRef    = (dive + 10 to bottom).find(i => series.horizontalSpeed(i) >= minHs).getOrElse(dive + 10 min bottom)
    val aroundNet = netChange(series, backRef, fwdRef)
    if Math.abs(aroundNet) < 5.0 then return CarveResult(0.0, dive, dive)
    val dir = if aroundNet > 0 then 1 else -1

    //  Expand forward from the dive point while the rotation persists  //
    var end    = dive
    var misses = 0
    var i      = dive + 1
    while i <= bottom do {
      smoothRate(i) match {
        case Some(r) if r * dir > tailRate => end = i; misses = 0
        case _                             =>
          misses += 1
          if misses > gap then { end = i - misses; i = bottom + 1 }
      }
      i += 1
    }

    //  Expand backward from the dive point  //
    var start = dive
    misses = 0
    i = dive
    while i > top + 1 do {
      smoothRate(i) match {
        case Some(r) if r * dir > tailRate => start = i - 1; misses = 0
        case _                             =>
          misses += 1
          if misses > gap then { start = i + misses - 1; i = 0 }
      }
      i -= 1
    }

    //  The carve must contain a core exceeding the core rate, otherwise it is not a carve  //
    val hasCore = (start to end).exists { k =>
      smoothRate(k).exists(r => r * dir > coreRate)
    }
    if !hasCore then return CarveResult(0.0, start, end)

    CarveResult(netChange(series, start, end), start, end)
  }

  /** Net heading change between two indices, wrapped to [-180, 180]. */
  private def netChange(series: PointSeries, from: Int, to: Int): Double =
    wrap(series.heading(to) - series.heading(from))

  private def wrap(raw: Double): Double =
    if raw > 180.0 then raw - 360.0 else if raw < -180.0 then raw + 360.0 else raw

  private def detectEvents(filePath: java.nio.file.Path): IO[FlightEvents] =
    TestFilesHandler
      .readCsv[CsvPoint](filePath)
      .through(JumpEventsDetector(DetectionConfig.default.jumpevents).detectEvents)
      .compile
      .last
      .map(_.getOrElse(FlightEvents.empty))

}
