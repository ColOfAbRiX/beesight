package com.colofabrix.scala.beesight.detection.bench

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.detection.{ FlightStagesDetection, FlysightTestReader }
import com.colofabrix.scala.beesight.model.*
import java.nio.file.Path
import scala.concurrent.duration.*

/**
 * End-to-end pipeline benchmark that runs the full detection pipeline on real jump data.
 *
 * Unlike JMH microbenchmarks, this measures wall-clock throughput of the entire stream pipeline
 * (preprocessing → detection → emission) on actual Flysight recordings.
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
    Path.of("beesight-detection/src/test/resources/bulk_detection_spec/flysight")

  def main(args: Array[String]): Unit =
    val saveBaseline    = args.contains("--save-baseline")
    val compareBaseline = args.contains("--compare")

    println("=== Beesight Pipeline Benchmark ===")
    println(s"JVM: ${System.getProperty("java.vm.name")} ${System.getProperty("java.version")}")
    println()

    val config   = DetectionConfig.default
    val zipFiles = collectZipFiles()

    println(s"Found ${zipFiles.size} jump files to benchmark")
    println()

    // Warmup
    println("Warming up (3 files)...")
    zipFiles.take(3).foreach(path => runSingleFile(config, path))
    println("Warmup complete")
    println()

    // Benchmark
    println("Running benchmark...")
    val results = zipFiles.map { path =>
      val result = runSingleFile(config, path)
      println(f"  ${result.filename}%-30s  ${result.totalPoints}%6d pts  ${result.wallClockMs}%6d ms  ${result.pointsPerSecond}%10.0f pts/s")
      result
    }

    val run = BenchmarkRun(
      timestamp = java.time.Instant.now().toString,
      jvm = s"${System.getProperty("java.vm.name")} ${System.getProperty("java.version")}",
      config = "default",
      files = results,
    )

    println()
    println("=== Summary ===")
    println(f"Total files:        ${run.files.size}")
    println(f"Total points:       ${run.totalPoints}")
    println(f"Total time:         ${run.totalMs} ms")
    println(f"Overall throughput: ${run.overallPointsPerSecond}%.0f points/sec")
    println()

    // Save baseline
    if saveBaseline then
      val path = BaselineStore.save(run)
      println(s"Baseline saved to: $path")
      println()

    // Compare against newest baseline
    if compareBaseline then
      BaselineStore.loadLatest() match {
        case Some((path, baseline)) =>
          println(s"Comparing against: $path")
          val comparison = BaselineStore.compare(baseline, run, tolerancePercent = 5.0)
          BaselineStore.printComparison(comparison)
          if comparison.hasRegression then
            println("\nREGRESSION DETECTED — exiting with error")
            sys.exit(1)
        case None =>
          println("No baseline found. Run with --save-baseline first.")
          sys.exit(1)
      }

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

  private def runSingleFile(config: DetectionConfig, filePath: Path): FileResult =
    val filename = filePath.getFileName.toString
    val detector = FlightStagesDetection(config)

    val start = System.nanoTime()
    val _     =
      FlysightTestReader
        .readCsvStream(filePath)
        .through(detector.streamDetect)
        .compile
        .last
        .unsafeRunSync()

    val elapsed = System.nanoTime() - start
    val ms      = elapsed / 1_000_000

    val pointCount = FlysightTestReader.countCsvLines(filePath)
    val pps        = if ms > 0 then pointCount.toDouble / (ms / 1000.0) else 0.0

    FileResult(filename, pointCount.toLong, ms, pps)

}
