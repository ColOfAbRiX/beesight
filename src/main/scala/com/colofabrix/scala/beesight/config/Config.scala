package com.colofabrix.scala.beesight.config

import cats.Show
import cats.syntax.all.*
import com.monovore.decline.*
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*
import io.github.iltotore.iron.decline.given
import java.nio.file.{ Path, Paths }

/**
 * Top-level application configuration assembled from CLI options.
 *
 * @param input Input path (file, directory, or glob pattern)
 * @param output Output directory (optional; defaults to sibling "processed" directory)
 * @param processLimit Maximum number of files to process (None = unlimited)
 * @param detectionConfig Algorithm thresholds and window sizes for flight stage detection
 * @param dataCutterConfig Settings for trimming output data around the jump
 * @param flags Boolean feature flags controlling output behaviour
 * @param parallelism Number of files to process concurrently (None = unbounded)
 */
final case class Config(
  input: Path,
  output: Option[Path],
  processLimit: Option[ProcessLimit],
  detectionConfig: BeesightConfig,
  dataCutterConfig: DataCutterConfig,
  flags: ConfigFlags,
  parallelism: Option[Parallelism],
)

object Config {

  val default: Config =
    Config(
      input = Path.of("."),
      output = None,
      processLimit = None,
      dataCutterConfig = DataCutterConfig.default,
      detectionConfig = BeesightConfig.default,
      flags = ConfigFlags.default,
      parallelism = Parallelism(1).some,
    )

  lazy val declineOpts: Opts[Config] =
    (
      input,
      output,
      processLimit,
      BeesightConfigCli.declineOpts,
      DataCutterConfig.declineOpts,
      ConfigFlags.declineOpts,
      parallelism,
    ).mapN(Config.apply)

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
        help = "Output directory where to place the produced CSV files.",
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

  private given showOption[A: Show]: Show[Option[A]] with
    def show(t: Option[A]): String =
      t.fold("Not configured")(Show[A].show)

}
