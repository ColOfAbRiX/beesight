package com.colofabrix.scala.beesight.config

import cats.data.{ Validated, ValidatedNel }
import com.monovore.decline.Opts
import com.typesafe.config.ConfigFactory
import io.github.iltotore.iron.*
import java.nio.file.Paths
import java.time.Duration
import pureconfig.*
import scala.io.Source

/**
 * CLI option definitions, HOCON parsing, and pureconfig loading for BeesightConfig.
 *
 * This lives in the CLI module because it couples the detection configuration data structure to command-line parsing
 * and configuration-loading concerns (decline options, HOCON overrides, and reference.conf defaults), which are not
 * intrinsic to the detection engine.
 */
object BeesightConfigCli {

  /**
   * Default detection configuration loaded from `reference.conf`.
   */
  val default: BeesightConfig =
    ConfigSource
      .default
      .loadOrThrow[BeesightConfig]

  lazy val declineOpts: Opts[BeesightConfig] =
    detectionConfigPath orElse detectionConfigString orElse Opts(default)

  private lazy val detectionConfigPath: Opts[BeesightConfig] =
    Opts
      .option[String](
        long = "detection-config-path",
        help = "Path to a HOCON file with override configuration for detection",
      )
      .mapValidated { path =>
        fromString(Source.fromFile(Paths.get(path).toFile).mkString)
      }

  private lazy val detectionConfigString: Opts[BeesightConfig] =
    Opts
      .option[String](
        long = "detection-config-string",
        help = "Override configuration for the detection config, in HOCON format",
      )
      .mapValidated(fromString)

  private def fromString(configString: String): ValidatedNel[String, BeesightConfig] =
    val overrides = ConfigFactory.parseString(configString)
    val defaults  = ConfigFactory.defaultReference()
    val merged    = overrides.withFallback(defaults).resolve()

    ConfigSource
      .fromConfig(merged)
      .load[BeesightConfig]
      .left
      .map(_.prettyPrint())
      .fold(
        err => Validated.invalidNel(err),
        cfg => Validated.valid(cfg),
      )

  //  ConfigReader Instances  //

  given ConfigReader[WindowSize] =
    ConfigReader[Int].map(i => WindowSize(i.refineUnsafe))

  given ConfigReader[Speed] =
    ConfigReader[Double].map(Speed(_))

  given ConfigReader[Duration] =
    ConfigReader[Long].map(Duration.ofSeconds)

  given ConfigReader[Altitude] =
    ConfigReader[Double].map(Altitude(_))

  given ConfigReader[Acceleration] =
    ConfigReader[Double].map(Acceleration(_))

  given ConfigReader[Mass] =
    ConfigReader[Double].map(Mass(_))

  given ConfigReader[Area] =
    ConfigReader[Double].map(Area(_))

  given ConfigReader[Angle] =
    ConfigReader[Double].map(Angle(_))

  given ConfigReader[TurnRate] =
    ConfigReader[Double].map(TurnRate(_))

  given ConfigReader[BeesightConfig] =
    ConfigReader.derived

  given ConfigReader[JumpEventsConfig] =
    ConfigReader.derived

  given ConfigReader[TakeoffConfig] =
    ConfigReader.derived

  given ConfigReader[FreefallConfig] =
    ConfigReader.derived

  given ConfigReader[CanopyConfig] =
    ConfigReader.derived

  given ConfigReader[LandingConfig] =
    ConfigReader.derived

  given ConfigReader[SwoopConfig] =
    ConfigReader.derived

  given ConfigReader[SwoopAltitudesConfig] =
    ConfigReader.derived

  given ConfigReader[SwoopSpeedsConfig] =
    ConfigReader.derived

  given ConfigReader[SwoopAnglesConfig] =
    ConfigReader.derived

  given ConfigReader[SwoopUnwrapConfig] =
    ConfigReader.derived

  given ConfigReader[SwoopGroundConfig] =
    ConfigReader.derived

  given ConfigReader[SwoopStreamConfig] =
    ConfigReader.derived

  given ConfigReader[MetricsConfig] =
    ConfigReader.derived

}
