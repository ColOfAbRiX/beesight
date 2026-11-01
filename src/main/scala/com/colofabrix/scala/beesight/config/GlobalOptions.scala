package com.colofabrix.scala.beesight.config

import cats.Show
import cats.syntax.all.*
import com.monovore.decline.Opts
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*
import io.github.iltotore.iron.decline.given
import java.nio.file.{ Path, Paths }

/**
 * Options shared by every command.
 *
 * @param input Input path (file, directory, or glob pattern)
 * @param output Output directory (optional; defaults to sibling "processed" directory)
 * @param processLimit Maximum number of files to process (None = unlimited)
 * @param parallelism Number of files to process concurrently (None = unbounded)
 * @param dryRun When true, run without writing any output file
 */
final case class GlobalOptions(
  input: Path,
  output: Option[Path],
  processLimit: Option[ProcessLimit],
  parallelism: Option[Parallelism],
  dryRun: Boolean,
)

object GlobalOptions {

  val default: GlobalOptions =
    GlobalOptions(
      input = Path.of("."),
      output = None,
      processLimit = None,
      parallelism = Parallelism(1).some,
      dryRun = false,
    )

  lazy val declineOpts: Opts[GlobalOptions] =
    (input, output, processLimit, parallelism, dryRun).mapN(GlobalOptions.apply)

  private lazy val input: Opts[Path] =
    Opts
      .option[String](
        long = "input",
        short = "i",
        help = "Input path (file or directory, supports glob patterns)",
      )
      .map(Paths.get(_))
      .withDefault(default.input)

  private lazy val output: Opts[Option[Path]] =
    Opts
      .option[String](
        long = "output",
        short = "o",
        help = "Output directory where to place the produced files.",
      )
      .map(Paths.get(_))
      .orNone

  private lazy val processLimit: Opts[Option[ProcessLimit]] =
    Opts
      .option[Int :| GreaterEqual[1]](
        long = "limit",
        short = "l",
        help = s"The maximum amount of files to process (default: ${default.processLimit.show})",
      )
      .map(x => Option(ProcessLimit.apply(x)))
      .withDefault(default.processLimit)

  private lazy val parallelism: Opts[Option[Parallelism]] =
    Opts
      .option[Int :| GreaterEqual[1]](
        long = "parallelism",
        help = s"Number of concurrent files to process (default: ${default.parallelism.show})",
      )
      .map(x => Option(Parallelism.apply(x)))
      .withDefault(default.parallelism)

  private lazy val dryRun: Opts[Boolean] =
    Opts
      .flag(
        long = "dry-run",
        short = "n",
        help = s"Toggles a run without writing any output file (default: ${default.dryRun})",
      )
      .map(_ => !default.dryRun)
      .withDefault(default.dryRun)

  private given showOption[A: Show]: Show[Option[A]] with
    def show(t: Option[A]): String =
      t.fold("Not configured")(Show[A].show)

}
