package com.colofabrix.scala.beesight.config

import cats.data.*
import cats.implicits.*
import com.monovore.decline.*
import java.nio.file.Path
import java.nio.file.Paths

object CliConfig {

  lazy val allOptions: Opts[Config] =
    (input, output, limit, dryRun, buffer, minPoints).mapN(Config.apply)

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
      .orFalse

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

}
