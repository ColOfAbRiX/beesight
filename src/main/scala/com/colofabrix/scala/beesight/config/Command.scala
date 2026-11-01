package com.colofabrix.scala.beesight.config

import cats.syntax.all.*
import com.colofabrix.scala.beesight.detection.config.BeesightConfig
import com.colofabrix.scala.beesight.pipeline.{ ResolvedStages, Stage }
import com.monovore.decline.{ Command as DeclineCommand, Opts }

/**
 * The command selected on the command line, determining which pipeline stages to execute.
 */
enum Command {

  case JumpEvents
  case Analyse
  case Convert
  case Metrics
  case Swoop
  case Charts
  case Cut

}

/**
 * The command selected on the command line together with the configurations carried by its own options.
 *
 * @param command The selected command
 * @param detectionConfig Detection configuration, accepted by the commands that run detection
 * @param dataCutterConfig Cut configuration, accepted by the commands that write the processed CSV
 */
final case class CommandOptions(
  command: Command,
  detectionConfig: BeesightConfig,
  dataCutterConfig: DataCutterConfig,
)

object Command {

  val default: Command =
    Command.JumpEvents

  private val defaultOptions: CommandOptions =
    CommandOptions(default, BeesightConfig.default, DataCutterConfig.default)

  lazy val declineOpts: Opts[CommandOptions] =
    subcommands
      .map(Opts.subcommand(_))
      .reduce(_ orElse _)
      .withDefault(defaultOptions)

  extension (self: Command) {

    /** The pipeline stages the command wants to execute */
    def stages: Set[Stage] =
      self match {
        case Command.Analyse    => Stage.all.toSet
        case Command.Charts     => Set(Stage.Charts)
        case Command.Convert    => Set(Stage.Convert)
        case Command.Cut        => Set(Stage.Cut)
        case Command.JumpEvents => Set(Stage.JumpEvents)
        case Command.Metrics    => Set(Stage.Metrics)
        case Command.Swoop      => Set(Stage.Swoop)
      }

    /** The execution plan for the command, with prerequisites auto-satisfied */
    def resolvedStages: ResolvedStages =
      ResolvedStages.of(self.stages)

  }

  private lazy val subcommands: List[DeclineCommand[CommandOptions]] = List(
    withDetectionAndCut(
      "jumpevents",
      "Detect the jump events and write the processed CSV (default command)",
      Command.JumpEvents,
    ),
    withDetectionAndCut(
      "analyse",
      "Run the full analysis: jump events, metrics, swoop and charts",
      Command.Analyse
    ),
    withDetection(
      "metrics",
      "Export the flight metrics of each file as CSV",
      Command.Metrics
    ),
    withDetection(
      "swoop",
      "Produce the swoop landing analysis report",
      Command.Swoop
    ),
    plain(
      "charts",
      "Generate the interactive HTML charts",
      Command.Charts
    ),
    plain(
      "convert",
      "Convert the input files to FlySight CSV format",
      Command.Convert
    ),
    withDetectionAndCut(
      "cut",
      "Write the processed CSV trimmed around the detected jump",
      Command.Cut
    ),
  )

  private def withDetectionAndCut(name: String, help: String, command: Command): DeclineCommand[CommandOptions] =
    DeclineCommand(name, help) {
      (BeesightConfigCli.declineOpts, DataCutterConfig.declineOpts)
        .mapN((detection, cutter) => CommandOptions(command, detection, cutter))
    }

  private def withDetection(name: String, help: String, command: Command): DeclineCommand[CommandOptions] =
    DeclineCommand(name, help) {
      BeesightConfigCli.declineOpts.map(detection => CommandOptions(command, detection, DataCutterConfig.default))
    }

  private def plain(name: String, help: String, command: Command): DeclineCommand[CommandOptions] =
    DeclineCommand(name, help)(Opts(CommandOptions(command, BeesightConfig.default, DataCutterConfig.default)))

}
