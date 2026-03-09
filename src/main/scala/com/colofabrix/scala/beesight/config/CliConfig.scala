package com.colofabrix.scala.beesight.config

import cats.implicits.*
import com.monovore.decline.*
import java.nio.file.{ Path, Paths }

object CliConfig {

  lazy val allOptions: Opts[Config] =
    (
      input,
      output,
      limit,
      dryRun,
      buffer,
      minPoints,
      writeCsv,
      createGraph,
      createSummary,
      parallelism,
      detectionConfig,
    ).mapN(Config.apply)

  lazy val input: Opts[Path] =
    Opts
      .option[String](
        long = "input",
        short = "i",
        help = "Input path (file or directory, supports glob patterns)",
      )
      .map(Paths.get(_))
      .withDefault(Config.default.input)

  lazy val output: Opts[Option[Path]] =
    Opts
      .option[String](
        long = "output",
        short = "o",
        help = "Output directory where to place the produced CSV files",
      )
      .map(Paths.get(_))
      .orNone

  lazy val detectionConfig: Opts[DetectionConfig] =
    Opts
      .option[String](
        long = "detection-config",
        help = "Output directory where to place the produced CSV files",
      )
      .map(_ => DetectionConfig.default) // TODO: Implement decoding
      .withDefault(DetectionConfig.default)

  lazy val limit: Opts[Option[Int]] =
    Opts
      .option[Int](
        long = "limit",
        short = "l",
        help = "The maximum amount of files to process",
      )
      .map(Math.max(0, _))
      .orNone

  lazy val buffer: Opts[Int] =
    Opts
      .option[Int](
        long = "buffer",
        help = "Number of points to keep before or after a landing or takeoff has been detected",
      )
      .map(Math.max(0, _))
      .withDefault(Config.default.bufferPoints)

  lazy val minPoints: Opts[Double] =
    Opts
      .option[Double](
        long = "min-retain-points",
        help = "Percentage of minimum point the tool must keep.",
      )
      .map(d => Math.min(1.0, Math.max(0.0, d)))
      .withDefault(Config.default.minRetainedPoints)

  lazy val dryRun: Opts[Boolean] =
    Opts
      .flag(
        long = "dry-run",
        short = "n",
        help = s"Toggles a run without processing the fiels (default: ${Config.default.dryRun})",
      )
      .map(_ => !Config.default.dryRun)
      .withDefault(Config.default.dryRun)

  lazy val writeCsv: Opts[Boolean] =
    Opts
      .flag(
        long = "write-csv",
        help = s"Toggles the write into output CSV (default: ${Config.default.writeCsv})",
      )
      .map(_ => !Config.default.writeCsv)
      .withDefault(Config.default.writeCsv)

  lazy val createGraph: Opts[Boolean] =
    Opts
      .flag(
        long = "create-graph",
        help = s"Toggles the creation of an output graph of the CSV data (default: ${Config.default.createGraph})",
      )
      .map(_ => !Config.default.createGraph)
      .withDefault(Config.default.createGraph)

  lazy val createSummary: Opts[Boolean] =
    Opts
      .flag(
        long = "create-summary",
        help = s"Toggles the creation an output summary of all the provessed CSV files (default: ${Config.default.createSummary})",
      )
      .map(_ => !Config.default.createSummary)
      .withDefault(Config.default.createSummary)

  lazy val parallelism: Opts[Option[Int]] =
    Opts
      .option[Int](
        long = "parallelism",
        help = "The number of concurrent threads. No limits by default",
      )
      .map(Math.max(0, _))
      .orNone

}
