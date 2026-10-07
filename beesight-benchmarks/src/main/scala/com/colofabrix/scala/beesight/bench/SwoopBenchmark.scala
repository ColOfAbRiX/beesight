package com.colofabrix.scala.beesight.detection.bench

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all.given
import com.colofabrix.scala.beesight.TestFilesHandler.given
import com.colofabrix.scala.beesight.detection.config.BeesightConfig
import com.colofabrix.scala.beesight.detection.jumpevents.JumpEventsDetector
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.detection.swoop.SwoopAnalyser
import com.colofabrix.scala.beesight.{ CsvPoint, TestFilesHandler }
import java.nio.file.{ Files, Path, Paths }

/**
 * Wall-clock benchmark for the swoop analysis stage (SwoopAnalyser) on real jump recordings.
 *
 * Unlike JMH microbenchmarks, this measures the wall-clock time of the full swoop pipeline (file read → decode →
 * index → cut → materialise → cascade computations) per recording. The flight events (takeoff, freefall, canopy,
 * landing) are pre-computed outside the timed section, so the measurement isolates the cost of the swoop detection
 * itself.
 *
 * Reliability improvements over raw single-pass timing mirror [[PipelineBenchmark]]:
 *   - Full-corpus warmup (all files, 3 passes) for JIT compilation
 *   - 3 measurement passes with per-file median aggregation
 *   - Pre-counted CSV rows to avoid counting during timing
 *   - Forced GC between warmup and measurement phases
 *
 * Run from sbt:
 *   sbt benchSwoop               # Run and print results
 *   sbt benchSwoopSaveBaseline   # Run and save as new timestamped baseline
 *   sbt benchSwoopCompare        # Run and compare against newest baseline
 */
object SwoopBenchmark {

  private val dataDir: Path =
    Vector(
      Paths.get("beesight-detection/src/test/resources/test_files/csv_files"),
      Paths.get("../beesight-detection/src/test/resources/test_files/csv_files"),
    ).find(Files.isDirectory(_))
      .getOrElse(Paths.get("beesight-detection/src/test/resources/test_files/csv_files"))

  private val WarmupPasses  = 3
  private val MeasurePasses = 3
  private val TolerancePct  = 5.0

  def main(args: Array[String]): Unit =
    val saveBaseline    = args.contains("--save-baseline")
    val compareBaseline = args.contains("--compare")

    println("=== Beesight Swoop Benchmark ===")
    println(s"JVM: ${System.getProperty("java.vm.name")} ${System.getProperty("java.version")}")
    println(s"Data: $dataDir")
    println(s"Warmup: $WarmupPasses full passes, Measurement: $MeasurePasses passes")
    println()

    val config         = BeesightConfig.default
    val eventsDetector = JumpEventsDetector(config.jumpevents)
    val swoopAnalyser  = SwoopAnalyser(config.swoop)
    val zipFiles       = collectZipFiles()

    if zipFiles.isEmpty then
      println("No jump files found — nothing to benchmark.")
      return

    println(s"Found ${zipFiles.size} jump files to benchmark")
    println()

    // ── Pre-compute the flight events (outside measurement) ──
    println("Pre-computing flight events...")
    val events: Map[String, FlightEvents] =
      zipFiles
        .parTraverse { path =>
          detectEvents(eventsDetector, path).map(path.getFileName.toString -> _)
        }
        .unsafeRunSync()
        .toMap
    println(s"Events ready for ${events.size} files")
    println()

    // ── Pre-count all data rows (outside measurement) ──
    println("Pre-counting data rows...")
    val pointCounts: Map[String, Long] =
      zipFiles.map { path =>
        path.getFileName.toString -> TestFilesHandler.countLines(path).toLong
      }.toMap
    val totalPoints = pointCounts.values.sum
    println(s"Total data points: $totalPoints")
    println()

    // ── Warmup: run ALL files multiple passes ──
    println(s"Warming up ($WarmupPasses full passes on all files)...")
    for pass <- 1 to WarmupPasses do
      print(s"  Warmup pass $pass/$WarmupPasses\r")
      zipFiles.foreach(path => timedRun(swoopAnalyser, events, path))
    println(s"Warmup complete ($WarmupPasses passes on ${zipFiles.size} files)")
    println()

    // ── Give the JVM a moment to settle ──
    System.gc()
    Thread.sleep(500)

    // ── Measurement: multiple passes ──
    println(s"Measuring ($MeasurePasses passes)...")
    val allPassTimings: Vector[Vector[(String, Long)]] = (1 to MeasurePasses).toVector.map { pass =>
      print(s"  Measure pass $pass/$MeasurePasses\r")
      zipFiles.map { path =>
        val filename = path.getFileName.toString
        val ms       = timedRun(swoopAnalyser, events, path)
        filename -> ms
      }
    }
    println(s"Measurement complete ($MeasurePasses passes)")
    println()

    // ── Aggregate: median wall-clock per file ──
    val fileResults: Vector[PipelineBenchmark.FileResult] =
      zipFiles.map { path =>
        val filename = path.getFileName.toString
        val points   = pointCounts.getOrElse(filename, 0L)
        val timings  = allPassTimings.map(_.find(_._1 == filename).map(_._2).getOrElse(0L))
        val medianMs = medianLong(timings)
        val pps      = if medianMs > 0 then points.toDouble / (medianMs / 1000.0) else 0.0
        PipelineBenchmark.FileResult(filename, points, medianMs, pps)
      }

    val run = PipelineBenchmark.BenchmarkRun(
      timestamp = java.time.Instant.now().toString,
      jvm = s"${System.getProperty("java.vm.name")} ${System.getProperty("java.version")}",
      config = "swoop",
      files = fileResults,
    )

    // ── Summary ──
    println("=== Summary ===")
    println(f"Total files:        ${run.files.size}")
    println(f"Total points:       ${run.totalPoints}")
    println(f"Total time:         ${run.totalMs} ms  (median across $MeasurePasses passes)")
    println(f"Overall throughput: ${run.overallPointsPerSecond}%.0f points/sec")
    println()

    // ── Per-file details ──
    println(f"=== Per-File Results (median of $MeasurePasses passes) ===")
    run.files.foreach { r =>
      val timings = allPassTimings.map(_.find(_._1 == r.filename).map(_._2).getOrElse(0L))
      val passStr = timings.map(ms => f"${ms}%4d ms").mkString("  ")
      println(f"  ${r.filename}%-30s  ${r.totalPoints}%6d pts  ${r.wallClockMs}%6d ms  | $passStr  ${r.pointsPerSecond}%10.0f pts/s")
    }
    println()

    // ── Save baseline ──
    if saveBaseline then
      val path = BaselineStore.save(run)
      println(s"Baseline saved to: $path")
      println()

    // ── Compare against newest baseline ──
    if compareBaseline then
      BaselineStore.loadLatest() match {
        case Some((path, baseline)) =>
          println(s"Comparing against: $path")
          val comparison = BaselineStore.compare(baseline, run, tolerancePercent = TolerancePct)
          BaselineStore.printComparison(comparison)
          if comparison.hasRegression then
            println("\nREGRESSION DETECTED — exiting with error")
            sys.exit(1)
        case None =>
          println("No baseline found. Run with --save-baseline first.")
          sys.exit(1)
      }

  // ─── Helpers ──────────────────────────────────────────────────────────

  private def collectZipFiles(): Vector[Path] =
    val dir = dataDir.toFile
    if !dir.exists() then
      println(s"Test data directory not found: $dataDir")
      Vector.empty
    else
      dir.listFiles()
        .filter(_.getName.toLowerCase.endsWith(".zip"))
        .sortBy(_.getName)
        .map(_.toPath)
        .toVector

  private def detectEvents(detector: JumpEventsDetector, filePath: Path): IO[FlightEvents] =
    TestFilesHandler
      .readCsv[CsvPoint](filePath)
      .through(detector.detect)
      .compile
      .last
      .map(_.getOrElse(FlightEvents.empty))

  /**
   * Timed run of a single file through the swoop detection pipeline. Returns wall-clock milliseconds.
   */
  private def timedRun(detector: SwoopAnalyser, events: Map[String, FlightEvents], filePath: Path): Long =
    val start = System.nanoTime()
    val _     =
      detector
        .analyse(
          events.getOrElse(filePath.getFileName.toString, FlightEvents.empty),
          TestFilesHandler.readCsv[CsvPoint](filePath)
        )
        .unsafeRunSync()
    val elapsed = System.nanoTime() - start
    elapsed / 1_000_000

  private def medianLong(values: Vector[Long]): Long =
    if values.isEmpty then 0L
    else
      val sorted = values.sorted
      val n      = sorted.size
      if n % 2 == 0 then (sorted(n / 2 - 1) + sorted(n / 2)) / 2
      else sorted(n / 2)

}
