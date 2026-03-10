package com.colofabrix.scala.beesight.config

import com.monovore.decline.Opts
import java.nio.file.Paths
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.*

final case class DetectionConfig(
  global: GlobalConfig,
  takeoff: TakeoffConfig,
  freefall: FreefallConfig,
  canopy: CanopyConfig,
  landing: LandingConfig,
)

object DetectionConfig {

  val default: DetectionConfig =
    DetectionConfig(
      global = GlobalConfig(
        preprocessWindowSize = 20,
        accelerationClip = 20.0,
      ),
      takeoff = TakeoffConfig(
        smoothingWindowSize = 5,
        backtrackWindowSize = 10,
        validationWindowSize = 100,
        validationVerticalSpeed = -4.932 * 0.5,
        verticalSpeedThreshold = -2.0,
        horizontalSpeedThreshold = 30.0,
        maxTakeoffAltitude = 600.0,
      ),
      freefall = FreefallConfig(
        verticalSpeedThreshold = 25.0,
        validationVerticalSpeed = 6.713 * 0.5,
        accelerationThreshold = 3.0,
        accelerationMinVelocity = 10.0,
        minFreefallAltitude = 600.0,
        smoothingWindowSize = 5,
        backtrackWindowSize = 10,
        validationWindowSize = 100,
      ),
      canopy = CanopyConfig(
        smoothingWindowSize = 5,
        backtrackWindowSize = 10,
        validationWindowSize = 100,
        verticalSpeedThreshold = 10.0,
        minCanopyAltitude = 600.0,
        validationVerticalSpeed = 6.713 * 0.2,
      ),
      landing = LandingConfig(
        smoothingWindowSize = 5,
        backtrackWindowSize = 10,
        validationWindowSize = 100,
        verticalSpeedThreshold = 1.0,
        horizontalSpeedThreshold = 2.0,
        stabilityThreshold = 0.5,
        validationVerticalSpeed = 0.05,
      ),
    )

  lazy val declineOpts: Opts[DetectionConfig] =
    detectionConfigPath orElse detectionConfigString orElse Opts(DetectionConfig.default)

  private lazy val detectionConfigPath: Opts[DetectionConfig] =
    Opts
      .option[String](
        long = "detection-config-path",
        help = "Override configuration for the detection config, in HOCON format",
      )
      .map(Paths.get(_))
      .map(_ => DetectionConfig.default)

  private lazy val detectionConfigString: Opts[DetectionConfig] =
    Opts
      .option[String](
        long = "detection-config-string",
        help = "Override configuration for the detection config, in HOCON format",
      )
      .map(_ => DetectionConfig.default)

  given Conversion[Int, WindowSize] with
    def apply(x: Int): WindowSize = WindowSize(x.refineUnsafe)

}

final case class GlobalConfig(
  preprocessWindowSize: WindowSize,
  accelerationClip: Double,
)

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
) extends EventConfigs

final case class FreefallConfig(
  smoothingWindowSize: WindowSize,
  backtrackWindowSize: WindowSize,
  validationWindowSize: WindowSize,
  verticalSpeedThreshold: Double,
  accelerationThreshold: Double,
  accelerationMinVelocity: Double,
  minFreefallAltitude: Double,
  validationVerticalSpeed: Double,
) extends EventConfigs

final case class CanopyConfig(
  smoothingWindowSize: WindowSize,
  backtrackWindowSize: WindowSize,
  validationWindowSize: WindowSize,
  verticalSpeedThreshold: Double,
  minCanopyAltitude: Double,
  validationVerticalSpeed: Double,
) extends EventConfigs

final case class LandingConfig(
  smoothingWindowSize: WindowSize,
  backtrackWindowSize: WindowSize,
  validationWindowSize: WindowSize,
  verticalSpeedThreshold: Double,
  stabilityThreshold: Double,
  validationVerticalSpeed: Double,
  horizontalSpeedThreshold: Double,
) extends EventConfigs
