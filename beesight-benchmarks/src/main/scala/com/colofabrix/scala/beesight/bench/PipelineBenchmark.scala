package com.colofabrix.scala.beesight.detection.bench

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.jumpevents.{ FlysightTestReader, JumpEventsDetector }
import com.colofabrix.scala.beesight.model.*
import java.nio.file.Path
import scala.concurrent.duration.*

/**
 * End-to-end pipeline benchmark that runs the full detection pipeline on real jump data.
 *
 * Unlike JMH microbenchmarks, this measures wall-clock throughput of the entire stream pipeline
 * (preprocessing → detection → emission) on actual Flysight recordings.
 *
 * Reliability improvements over raw single-pass timing:
 *   - Full-file warmup (all files, 3 passes) for JIT compilation
 *   - 3 measurement passes with per-file median aggregation
 *   - Reuses the same JumpEventsDetector instance across files
 *   - Pre-counts CSV rows before measurement to avoid I/O during timing
 *   - Forces GC between warmup and measurement phases
 *
 * Run from sbt:
 *   sbt benchPipeline                        # Run and print results
 *   sbt benchSaveBaseline                    # Run and save as new timestamped baseline
 *   sbt benchCompare                         # Run and compare against newest baseline
 *   sbt "bench; benchSaveBaseline"           # Run all benchmarks and save baseline
 */
object PipelineBenchmark {

  case class FileResult(
    filename: String,
    totalPoints: Long,
    wallClockMs: Long,
    pointsPerSecond: Double,
  )

  case class BenchmarkRun(
    timestamp: String,
    jvm: String,
    config: String,
    files: Vector[FileResult],
  ) {
    def totalPoints: Long =
      files.map(_.totalPoints).sum
    def totalMs: Long =
      files.map(_.wallClockMs).sum
    def overallPointsPerSecond: Double =
      if totalMs == 0 then 0.0
      else totalPoints.toDouble / (totalMs / 1000.0)
  }

  private val flysightDir =
    Path.of("beesight-detection/src/test/resources/jumpevents/bulk_detection_spec/flysight")

  private val WarmupPasses  = 3
  private val MeasurePasses = 3
  private val TolerancePct  = 5.0

  def main(args: Array[String]): Unit =
    val saveBaseline    = args.contains("--save-baseline")
    val compareBaseline = args.contains("--compare")

    println("=== Beesight Pipeline Benchmark ===")
    println(s"JVM: ${System.getProperty("java.vm.name")} ${System.getProperty("java.version")}")
    println(s"Warmup: $WarmupPasses full passes, Measurement: $MeasurePasses passes")
    println()

    val config   = DetectionConfig.default
    val detector = JumpEventsDetector(config)
    val zipFiles = collectZipFiles()

    if zipFiles.isEmpty then
      println("No jump files found — nothing to benchmark.")
      return

    println(s"Found ${zipFiles.size} jump files to benchmark")
    println()

    // ── Pre-count all data rows (outside measurement) ──
    println("Pre-counting data rows...")
    val pointCounts: Map[String, Long] =
      zipFiles.map { path =>
        path.getFileName.toString -> FlysightTestReader.countCsvLines(path).toLong
      }.toMap
    val totalPoints = pointCounts.values.sum
    println(s"Total data points: $totalPoints")
    println()

    // ── Warmup: run ALL files multiple passes ──
    println(s"Warming up ($WarmupPasses full passes on all files)...")
    for pass <- 1 to WarmupPasses do
      print(s"  Warmup pass $pass/$WarmupPasses\r")
      zipFiles.foreach(path => timedRun(detector, path))
    println(s"Warmup complete ($WarmupPasses passes on ${zipFiles.size} files)")
    println()

    // ── Give the JVM a moment to settle ──
    System.gc()
    Thread.sleep(500)

    // ── Measurement: multiple passes ──
    println(s"Measuring ($MeasurePasses passes)...")
    // For each pass, collect per-file timings
    val allPassTimings: Vector[Vector[(String, Long)]] = (1 to MeasurePasses).toVector.map { pass =>
      print(s"  Measure pass $pass/$MeasurePasses\r")
      val timings =
        zipFiles.map { path =>
          val filename = path.getFileName.toString
          val ms       = timedRun(detector, path)
          filename -> ms
        }
      timings
    }
    println(s"Measurement complete ($MeasurePasses passes)")
    println()

    // ── Aggregate: median wall-clock per file ──
    val fileResults: Vector[FileResult] =
      zipFiles.map { path =>
        val filename = path.getFileName.toString
        val points   = pointCounts.getOrElse(filename, 0L)
        val timings  = allPassTimings.map(_.find(_._1 == filename).map(_._2).getOrElse(0L))
        val medianMs = medianLong(timings)
        val pps      = if medianMs > 0 then points.toDouble / (medianMs / 1000.0) else 0.0
        FileResult(filename, points, medianMs, pps)
      }

    val run = BenchmarkRun(
      timestamp = java.time.Instant.now().toString,
      jvm = s"${System.getProperty("java.vm.name")} ${System.getProperty("java.version")}",
      config = "default",
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
      // Show per-pass timing for visibility
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
    val dir = flysightDir.toFile
    if !dir.exists() then
      println(s"Test data directory not found: $flysightDir")
      Vector.empty
    else
      dir.listFiles()
        .filter(_.getName.toLowerCase.endsWith(".zip"))
        .sortBy(_.getName)
        .map(_.toPath)
        .toVector

  /**
   * Timed run of a single file through the pipeline.
   * Returns wall-clock milliseconds.
   */
  private def timedRun(detector: JumpEventsDetector, filePath: Path): Long =
    val start = System.nanoTime()
    val _     =
      FlysightTestReader
        .readCsvStream(filePath)
        .through(detector.streamDetect)
        .compile
        .last
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
