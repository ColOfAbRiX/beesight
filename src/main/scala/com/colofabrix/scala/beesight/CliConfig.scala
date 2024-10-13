package com.colofabrix.scala.beesight

import better.files.File
import cats.data.*
import cats.implicits.*
import com.monovore.decline.*

final case class Config(
  input: File,
  output: Option[File],
  processLimit: Option[Int],
  dryRun: Boolean,
)

final case class DetectionConfig(
  windowTime: Int,
  takeoffThreshold: Double,
  landingThreshold: Double,
  ignoreLandingAbove: Double,
  bufferPoints: Int,
)

object Config:

  lazy val allOptions: Opts[Config] =
    (input, output, limit, dryRun)
      .mapN(Config.apply)
      .validate("Output directory must not match input directory")(config => config.input != config.output)

  lazy val input: Opts[File] =
    Opts
      .option[String](
        long = "input",
        short = "i",
        help = "Input directory where to discover CSV files",
      )
      .mapValidated(validateDirectory)
      .withDefault(File.currentWorkingDirectory)

  lazy val output: Opts[Option[File]] =
    Opts
      .option[String](
        long = "output",
        short = "i",
        help = "Output directory where to place the produced CSV files",
      )
      .mapValidated(validateDirectory)
      .orNone

  lazy val limit: Opts[Option[Int]] =
    Opts
      .option[Int](
        long = "limit",
        short = "l",
        help = "The maximum amount of files to process",
      )
      .orNone

  lazy val dryRun: Opts[Boolean] =
    Opts
      .flag(
        long = "dry-run",
        short = "n",
        help = "Shows the list of files that will be processed",
      )
      .orFalse

  private def validateDirectory(path: String): ValidatedNel[String, File] =
    val file = File(path)
    Validated.condNel(
      file.isDirectory,
      file,
      s"Path '$path' doesn't correspond to a valid directory.",
    )
