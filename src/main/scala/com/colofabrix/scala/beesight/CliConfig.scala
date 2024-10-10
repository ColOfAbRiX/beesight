package com.colofabrix.scala.beesight

import better.files.File
import cats.data.*
import cats.implicits.*
import com.monovore.decline.*

final case class Config(input: File, output: File)

object Config:

  lazy val allOptions: Opts[Config] =
    (input, output)
      .mapN(Config.apply)
      .validate("Output directory must not match input directory")(config => config.input != config.output)

  lazy val input: Opts[File] =
    Opts
      .option[String](
        "input",
        short = "i",
        help = "Input directory where to discover CSV files",
      )
      .mapValidated(validateDirectory)
      .withDefault(File.currentWorkingDirectory)

  lazy val output: Opts[File] =
    Opts
      .option[String](
        "output",
        short = "i",
        help = "Output directory where to place the produced CSV files",
      )
      .mapValidated(validateDirectory)
      .withDefault(File.currentWorkingDirectory / "processed")

  private def validateDirectory(path: String): ValidatedNel[String, File] =
    val file = File(path)
    Validated.condNel(
      file.isDirectory,
      file,
      s"Path '$path' doesn't correspond to a valid directory.",
    )
