package com.colofabrix.scala.beesight.config

import cats.data.{ Validated, ValidatedNel }
import com.monovore.decline.Opts
import com.typesafe.config.ConfigFactory
import io.github.iltotore.iron.*
import java.nio.file.Paths
import pureconfig.*
import scala.io.Source

/**
 * CLI option definitions, HOCON parsing, and pureconfig loading for DetectionConfig.
 *
 * This lives in the CLI module because it couples the detection configuration data structure to command-line parsing
 * and configuration-loading concerns (decline options, HOCON overrides, and reference.conf defaults), which are not
 * intrinsic to the detection engine.
 */
object DetectionConfigCli {

  /**
   * Default detection configuration loaded from `reference.conf`.
   */
  val default: DetectionConfig =
    ConfigSource
      .default
      .loadOrThrow[DetectionConfig]

  lazy val declineOpts: Opts[DetectionConfig] =
    detectionConfigPath orElse detectionConfigString orElse Opts(default)

  private lazy val detectionConfigPath: Opts[DetectionConfig] =
    Opts
      .option[String](
        long = "detection-config-path",
        help = "Path to a HOCON file with override configuration for detection",
      )
      .mapValidated { path =>
        fromString(Source.fromFile(Paths.get(path).toFile).mkString)
      }

  private lazy val detectionConfigString: Opts[DetectionConfig] =
    Opts
      .option[String](
        long = "detection-config-string",
        help = "Override configuration for the detection config, in HOCON format",
      )
      .mapValidated(fromString)

  private def fromString(configString: String): ValidatedNel[String, DetectionConfig] =
    val overrides = ConfigFactory.parseString(configString)
    val defaults  = ConfigFactory.defaultReference()
    val merged    = overrides.withFallback(defaults).resolve()

    ConfigSource
      .fromConfig(merged)
      .load[DetectionConfig]
      .left
      .map(_.prettyPrint())
      .fold(
        err => Validated.invalidNel(err),
        cfg => Validated.valid(cfg),
      )

  //  ConfigReader Instances  //

  given ConfigReader[WindowSize] =
    ConfigReader[Int].map(i => WindowSize(i.refineUnsafe))

  given ConfigReader[DetectionConfig] =
    ConfigReader.derived

  given ConfigReader[GlobalConfig] =
    ConfigReader.derived

  given ConfigReader[TakeoffConfig] =
    ConfigReader.derived

  given ConfigReader[FreefallConfig] =
    ConfigReader.derived

  given ConfigReader[CanopyConfig] =
    ConfigReader.derived

  given ConfigReader[LandingConfig] =
    ConfigReader.derived

}
