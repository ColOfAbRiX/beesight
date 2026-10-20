package com.colofabrix.scala.beesight.config

import cats.syntax.all.*
import com.colofabrix.scala.beesight.detection.config.*
import com.monovore.decline.Opts

/**
 * Top-level application configuration assembled from the CLI.
 *
 * @param global Options shared by every command
 * @param detectionConfig Algorithm thresholds and window sizes for flight stage detection
 * @param dataCutterConfig Settings for trimming output data around the jump
 * @param command The command selected on the command line
 */
final case class Config(
  global: GlobalOptions,
  detectionConfig: BeesightConfig,
  dataCutterConfig: DataCutterConfig,
  command: Command,
)

object Config {

  val default: Config =
    Config(
      global = GlobalOptions.default,
      detectionConfig = BeesightConfig.default,
      dataCutterConfig = DataCutterConfig.default,
      command = Command.default,
    )

  /**
   * Decline-style CLI options assembling a [[Config]], falling back to [[default]] for omitted options.
   */
  lazy val declineOpts: Opts[Config] =
    (
      GlobalOptions.declineOpts,
      BeesightConfigCli.declineOpts,
      DataCutterConfig.declineOpts,
      Command.declineOpts,
    ).mapN(Config.apply)

}
