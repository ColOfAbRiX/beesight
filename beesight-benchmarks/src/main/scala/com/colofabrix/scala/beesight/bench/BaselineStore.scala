package com.colofabrix.scala.beesight.detection.bench

import java.nio.file.{ Files, Path, Paths }
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Stores and compares benchmark results against git-tracked baseline files.
 *
 * Baselines are stored in `beesight-benchmarks/src/main/resources/baselines/` with timestamped filenames
 * (e.g. `2026-08-25T17-30-00Z.txt`). When comparing, the newest baseline is automatically selected.
 *
 * File format is a simple CSV-like text file for easy diffing in PRs:
 * {{{
 * # Beesight Benchmark Baseline
 * # timestamp: 2026-08-25T17:30:00Z
 * # jvm: OpenJDK 64-Bit Server VM 25.0.3
 * # config: default
 * # total_points: 1276743
 * # total_ms: 26128
 * # overall_pps: 48865
 * filename,points,wall_clock_ms,points_per_second
 * Jump 796.zip,3000,45,66666
 * Jump 797.zip,5200,78,66667
 * ...
 * }}}
 */
object BaselineStore {

  private val baselinesDir: Path =
    Paths.get("beesight-benchmarks/src/main/resources/baselines")

  case class Comparison(
    baseline: PipelineBenchmark.BenchmarkRun,
    current: PipelineBenchmark.BenchmarkRun,
    fileResults: Vector[FileComparison],
    tolerancePercent: Double,
  ) {

    def hasRegression: Boolean =
      fileResults.exists(_.isRegression) || overallRegression

    def overallRegression: Boolean =
      val baselinePps = baseline.overallPointsPerSecond
      val currentPps  = current.overallPointsPerSecond
      if baselinePps <= 0 then
        false
      else
        ((baselinePps - currentPps) / baselinePps * 100.0) > tolerancePercent

  }

  case class FileComparison(
    filename: String,
    baselinePps: Double,
    currentPps: Double,
    changePercent: Double,
    tolerancePercent: Double,
  ) {
    def isRegression: Boolean =
      changePercent > tolerancePercent
    def isImprovement: Boolean =
      changePercent < -tolerancePercent
  }

  /** Save a benchmark run as a new timestamped baseline file. */
  def save(run: PipelineBenchmark.BenchmarkRun): Path =
    Files.createDirectories(baselinesDir)

    val timestamp =
      DateTimeFormatter
        .ofPattern("yyyy-MM-dd'T'HH-mm-ss'Z'")
        .format(java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC))

    val path = baselinesDir.resolve(s"$timestamp.txt")

    val sb = new StringBuilder
    sb.append("# Beesight Benchmark Baseline\n")
    sb.append(s"# timestamp: ${run.timestamp}\n")
    sb.append(s"# jvm: ${run.jvm}\n")
    sb.append(s"# config: ${run.config}\n")
    sb.append(s"# total_points: ${run.totalPoints}\n")
    sb.append(s"# total_ms: ${run.totalMs}\n")
    sb.append(f"# overall_pps: ${run.overallPointsPerSecond}%.0f\n")
    sb.append("filename,points,wall_clock_ms,points_per_second\n")

    run.files.foreach { f =>
      sb.append(s"${f.filename},${f.totalPoints},${f.wallClockMs},${f.pointsPerSecond.toInt}\n")
    }

    val _ = Files.writeString(path, sb.toString())

    path

  /** Find the newest baseline file in the baselines directory. */
  def latestBaseline(): Option[Path] =
    if !Files.exists(baselinesDir) then
      return None

    import scala.jdk.StreamConverters.*

    val files =
      Files
        .list(baselinesDir)
        .filter(p => Files.isRegularFile(p) && p.toString.endsWith(".txt"))
        .toScala(List)
        .sortBy(p => Files.getLastModifiedTime(p).toMillis)
        .reverse

    files.headOption

  /** Load a baseline from a specific path. */
  def load(path: Path): Option[PipelineBenchmark.BenchmarkRun] =
    if !Files.exists(path) then
      return None

    val lines =
      Files
        .readAllLines(path)
        .toArray(Array.empty[String])
        .toVector

    val metadata =
      lines
        .filter(_.startsWith("# "))
        .flatMap { line =>
          val content = line.stripPrefix("# ")
          content.split(":", 2) match {
            case Array(key, value) => Some(key.trim -> value.trim)
            case _                 => None
          }
        }
        .toMap

    val dataLines =
      lines
        .filterNot(_.startsWith("#"))
        .filter(_.trim.nonEmpty)

    if dataLines.isEmpty then
      return None

    val fileResults = dataLines.tail.flatMap { line =>
      line.split(",", -1) match {
        case Array(filename, points, ms, pps) =>
          Vector(PipelineBenchmark.FileResult(filename, points.toLong, ms.toLong, pps.toDouble))
        case _ =>
          Vector.empty
      }
    }

    Some(PipelineBenchmark.BenchmarkRun(
      timestamp = metadata.getOrElse("timestamp", "unknown"),
      jvm = metadata.getOrElse("jvm", "unknown"),
      config = metadata.getOrElse("config", "default"),
      files = fileResults,
    ))

  /** Load the newest baseline automatically. */
  def loadLatest(): Option[(Path, PipelineBenchmark.BenchmarkRun)] =
    latestBaseline().flatMap { path =>
      load(path).map(path -> _)
    }

  def compare(
    baseline: PipelineBenchmark.BenchmarkRun,
    current: PipelineBenchmark.BenchmarkRun,
    tolerancePercent: Double,
  ): Comparison =
    val baselineByName = baseline.files.map(f => f.filename -> f).toMap
    val currentByName  = current.files.map(f => f.filename -> f).toMap
    val allFiles       = (baselineByName.keySet ++ currentByName.keySet).toVector.sorted

    val fileResults =
      allFiles.map { filename =>
        val bPps   = baselineByName.get(filename).map(_.pointsPerSecond).getOrElse(0.0)
        val cPps   = currentByName.get(filename).map(_.pointsPerSecond).getOrElse(0.0)
        val change = if bPps > 0 then (bPps - cPps) / bPps * 100.0 else 0.0
        FileComparison(filename, bPps, cPps, change, tolerancePercent)
      }

    Comparison(baseline, current, fileResults, tolerancePercent)

  def printComparison(comp: Comparison): Unit =
    println("=== Baseline Comparison ===")
    println(f"Baseline: ${comp.baseline.timestamp}  (${comp.baseline.overallPointsPerSecond}%.0f pts/s)")
    println(f"Current:  ${comp.current.timestamp}  (${comp.current.overallPointsPerSecond}%.0f pts/s)")
    println(f"Tolerance: ${comp.tolerancePercent}%.1f%%")
    println()

    val regressions  = comp.fileResults.filter(_.isRegression)
    val improvements = comp.fileResults.filter(_.isImprovement)
    val unchanged    = comp.fileResults.filterNot(r => r.isRegression || r.isImprovement)

    if regressions.nonEmpty then
      println(s"REGRESSIONS (${regressions.size}):")
      regressions.foreach { r =>
        println(f"  ${r.filename}%-30s  ${r.baselinePps}%10.0f → ${r.currentPps}%10.0f  (${r.changePercent}%+.1f%%)")
      }
      println()

    if improvements.nonEmpty then
      println(s"IMPROVEMENTS (${improvements.size}):")
      improvements.foreach { r =>
        println(f"  ${r.filename}%-30s  ${r.baselinePps}%10.0f → ${r.currentPps}%10.0f  (${r.changePercent}%+.1f%%)")
      }
      println()

    println(s"Unchanged: ${unchanged.size} files")

    val baselinePps   = comp.baseline.overallPointsPerSecond
    val overallChange =
      if baselinePps > 0 then
        (baselinePps - comp.current.overallPointsPerSecond) / baselinePps * 100.0
      else
        0.0

    println(f"\nOverall throughput change: ${overallChange}%+.1f%%")
    if comp.hasRegression then
      println("STATUS: REGRESSION DETECTED")
    else
      println("STATUS: OK")
}
