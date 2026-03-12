package com.colofabrix.scala.beesight.config

import cats.data.*
import com.monovore.decline.Opts
import com.typesafe.config.ConfigFactory
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.*
import java.nio.file.Paths
import pureconfig.*
import scala.io.Source

final case class DetectionConfig(
  global: GlobalConfig,
  takeoff: TakeoffConfig,
  freefall: FreefallConfig,
  canopy: CanopyConfig,
  landing: LandingConfig,
) derives ConfigReader

object DetectionConfig {

  val default: DetectionConfig =
    ConfigSource
      .default
      .loadOrThrow[DetectionConfig]

  lazy val declineOpts: Opts[DetectionConfig] =
    detectionConfigPath orElse detectionConfigString orElse Opts(DetectionConfig.default)

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

}

final case class GlobalConfig(
  preprocessWindowSize: WindowSize,
  accelerationClip: Double,
) derives ConfigReader

sealed trait EventConfigs {
  def smoothingWindowSize: WindowSize
  def backtrackWindowSize: WindowSize
  def validationWindowSize: WindowSize
}

final case class TakeoffConfig(
  smoothingWindowSize: WindowSize,
  backtrackWindowSize: WindowSize,
  validationWindowSize: WindowSize,
  validationVerticalSpeed: Double,
  verticalSpeedThreshold: Double,
  horizontalSpeedThreshold: Double,
  maxTakeoffAltitude: Double,
) extends EventConfigs derives ConfigReader

final case class FreefallConfig(
  smoothingWindowSize: WindowSize,
  backtrackWindowSize: WindowSize,
  validationWindowSize: WindowSize,
  verticalSpeedThreshold: Double,
  accelerationThreshold: Double,
  accelerationMinVelocity: Double,
  minFreefallAltitude: Double,
  validationVerticalSpeed: Double,
) extends EventConfigs derives ConfigReader

final case class CanopyConfig(
  smoothingWindowSize: WindowSize,
  backtrackWindowSize: WindowSize,
  validationWindowSize: WindowSize,
  verticalSpeedThreshold: Double,
  minCanopyAltitude: Double,
  validationVerticalSpeed: Double,
) extends EventConfigs derives ConfigReader

final case class LandingConfig(
  smoothingWindowSize: WindowSize,
  backtrackWindowSize: WindowSize,
  validationWindowSize: WindowSize,
  verticalSpeedThreshold: Double,
  stabilityThreshold: Double,
  validationVerticalSpeed: Double,
  horizontalSpeedThreshold: Double,
) extends EventConfigs derives ConfigReader

given ConfigReader[WindowSize] =
  ConfigReader[Int].map(i => WindowSize(i.refineUnsafe))
