package com.colofabrix.scala.beesight

import better.files.File
import cats.data.*
import cats.implicits.*
import com.monovore.decline.*
import scala.util.matching.Regex
import scala.util.Try

object CliConfig:

  lazy val allOptions: Opts[Config] =
    (input, output, limit, dryRun, peakDetectionParams)
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

  lazy val peakDetectionParams: Opts[DetectionConfig] =
    Opts
      .option(
        long = "peak-detection-params",
        short = "p",
        help = "Parameters to fine tune the peak detection.",
      )
      .map(parsePeakDetectionParams)
      .withDefault(DetectionConfig.Default)

  private def validateDirectory(path: String): ValidatedNel[String, File] =
    val file = File(path)
    Validated.condNel(
      file.isDirectory,
      file,
      s"Path '$path' doesn't correspond to a valid directory.",
    )

  private val keyValueRegex: Regex =
    "^\\s*(\\w+)\\s*=\\s*([^,]*)\\s*$".r

  private def parsePeakDetectionParams(params: String): DetectionConfig =
    val splitValues =
      for
        pair    <- params.split(",").toList
        matches <- keyValueRegex.findAllMatchIn(pair).toList
      yield (matches.group(1).toLowerCase, matches.group(2))

    val paramsMap = splitValues.toMap

    DetectionConfig(
      WindowTime = paramsMap.getInt("windowTime", DetectionConfig.Default.WindowTime),
      TakeoffThreshold = paramsMap.getDouble("takeoffThreshold", DetectionConfig.Default.TakeoffThreshold),
      Influence = paramsMap.getDouble("influence", DetectionConfig.Default.TakeoffThreshold),
      LandingThreshold = paramsMap.getDouble("landingThreshold", DetectionConfig.Default.LandingThreshold),
      IgnoreLandingAbove = paramsMap.getDouble("ignoreLandingAbove", DetectionConfig.Default.IgnoreLandingAbove),
      BufferPoints = paramsMap.getInt("bufferPoints", DetectionConfig.Default.BufferPoints),
      MinRetainedPoints = paramsMap.getDouble("minRetainedPoints", DetectionConfig.Default.MinRetainedPoints),
    )

  extension (self: Map[String, String])

    def getInt(key: String, default: => Int): Int =
      self
        .get(key.toLowerCase)
        .flatMap(_.toIntOption)
        .getOrElse(default)

    def getDouble(key: String, default: => Double): Double =
      self
        .get(key.toLowerCase)
        .flatMap(_.toDoubleOption)
        .getOrElse(default)
