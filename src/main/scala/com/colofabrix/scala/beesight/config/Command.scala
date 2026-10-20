package com.colofabrix.scala.beesight.config

import cats.syntax.all.*
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

object Command {

  val default: Command =
    Command.JumpEvents

  /**
   * Decline-style CLI options selecting a [[Command]], defaulting to [[default]] when no subcommand is given.
   */
  lazy val declineOpts: Opts[Command] =
    subcommands
      .map(Opts.subcommand(_))
      .reduce(_ orElse _)
      .withDefault(default)

  extension (self: Command) {

    /** The pipeline stages the command wants to execute */
    def stages: Set[Stage] = self match {
      case Command.JumpEvents => Set(Stage.JumpEvents)
      case Command.Analyse    => Stage.all.toSet
      case Command.Convert    => Set(Stage.Convert)
      case Command.Metrics    => Set(Stage.Metrics)
      case Command.Swoop      => Set(Stage.Swoop)
      case Command.Charts     => Set(Stage.Charts)
      case Command.Cut        => Set(Stage.Cut)
    }

    /** The execution plan for the command, with prerequisites auto-satisfied */
    def resolvedStages: ResolvedStages =
      ResolvedStages.of(self.stages)

  }

  private lazy val subcommands: List[DeclineCommand[Command]] = List(
    DeclineCommand("jumpevents", "Detect the jump events and write the processed CSV (default command)")(
      Opts(default),
    ),
    DeclineCommand("analyse", "Run the full analysis: jump events, metrics, swoop and charts")(
      Opts(Command.Analyse),
    ),
    DeclineCommand("convert", "Convert the input files to FlySight CSV format")(
      Opts(Command.Convert),
    ),
    DeclineCommand("metrics", "Export the flight metrics of each file as CSV")(
      Opts(Command.Metrics),
    ),
    DeclineCommand("swoop", "Produce the swoop landing analysis report")(
      Opts(Command.Swoop),
    ),
    DeclineCommand("charts", "Generate the interactive HTML charts")(
      Opts(Command.Charts),
    ),
    DeclineCommand("cut", "Write the processed CSV trimmed around the detected jump")(
      Opts(Command.Cut),
    ),
  )

}
