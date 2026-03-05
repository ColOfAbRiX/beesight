package com.colofabrix.scala.beesight.config

import cats.data.*
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
      createSummary
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

  lazy val limit: Opts[Option[Int]] =
    Opts
      .option[Int](
        long = "limit",
        short = "l",
        help = "The maximum amount of files to process",
      )
      .map(Math.max(0, _))
      .orNone

  lazy val dryRun: Opts[Boolean] =
    Opts
      .flag(
        long = "dry-run",
        short = "n",
        help = "Shows the list of files that will be processed",
      )
      .map(_ => true)
      .withDefault(Config.default.dryRun)

  lazy val buffer: Opts[Int] =
    Opts
      .option[Int](
        long = "buffer",
        short = "b",
        help = "Number of points to keep before or after a landing or takeoff has been detected",
      )
      .map(Math.max(0, _))
      .withDefault(Config.default.bufferPoints)

  lazy val minPoints: Opts[Double] =
    Opts
      .option[Double](
        long = "min-retain-points",
        short = "k",
        help = "Percentage of minimum point the tool must keep.",
      )
      .map(d => Math.min(1.0, Math.max(0.0, d)))
      .withDefault(Config.default.minRetainedPoints)

  lazy val writeCsv: Opts[Boolean] =
    Opts
      .flag(
        long = "write-csv",
        short = "c",
        help = "Shows the list of files that will be processed",
      )
      .map(_ => true)
      .withDefault(Config.default.writeCsv)

  lazy val createGraph: Opts[Boolean] =
    Opts
      .flag(
        long = "create-graph",
        short = "g",
        help = "Creates an output graph of the CSV data",
      )
      .map(_ => true)
      .withDefault(Config.default.createGraph)

  lazy val createSummary: Opts[Boolean] =
    Opts
      .flag(
        long = "create-summary",
        help = "Creates an output summary of all the provessed CSV files",
      )
      .map(_ => true)
      .withDefault(Config.default.createSummary)

}
