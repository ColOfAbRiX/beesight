package com.colofabrix.scala.beesight.config

import cats.syntax.all.*
import com.monovore.decline.Opts

final case class ConfigFlags(
  dryRun: Boolean,
  writeCsv: Boolean,
  createGraph: Boolean,
  createSummary: Boolean,
)

object ConfigFlags {

  val default: ConfigFlags =
    ConfigFlags(
      dryRun = false,
      writeCsv = true,
      createGraph = false,
      createSummary = false,
    )

  lazy val declineOpts: Opts[ConfigFlags] =
    (dryRun, writeCsv, createGraph, createSummary).mapN(ConfigFlags.apply)

  private lazy val dryRun: Opts[Boolean] =
    Opts
      .flag(
        long = "dry-run",
        short = "n",
        help = s"Toggles a run without processing the fiels (default: ${default.dryRun})",
      )
      .map(_ => !default.dryRun)
      .withDefault(default.dryRun)

  private lazy val writeCsv: Opts[Boolean] =
    Opts
      .flag(
        long = "toggle-write-csv",
        help = s"Toggles the write into output CSV (default: ${default.writeCsv})",
      )
      .map(_ => !default.writeCsv)
      .withDefault(default.writeCsv)

  private lazy val createGraph: Opts[Boolean] =
    Opts
      .flag(
        long = "toggle-create-graph",
        help = s"Toggles the creation of the output graph of the CSV data (default: ${default.createGraph})",
      )
      .map(_ => !default.createGraph)
      .withDefault(default.createGraph)

  private lazy val createSummary: Opts[Boolean] =
    Opts
      .flag(
        long = "toggle-create-summary",
        help = s"Toggles the creation of the output summary (default: ${default.createSummary})",
      )
      .map(_ => !default.createSummary)
      .withDefault(default.createSummary)

}
