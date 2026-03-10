package com.colofabrix.scala.beesight.config

import cats.syntax.all.*
import com.monovore.decline.*
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*
import io.github.iltotore.iron.decline.given
import java.nio.file.{ Path, Paths }

/**
 * Configuration for the command line tool
 *
 * @param input Input path (file or directory, supports glob patterns)
 * @param output Output directory (optional, defaults to sibling "processed" directory)
 * @param processLimit Limits the number of files to process
 * @param dryRun Do a test run without creating output files
 * @param bufferPoints Number of points to keep before or after a landing or takeoff has been detected
 * @param minRetainedPoints Percentage of minimum point the tool must keep
 */
final case class Config(
  input: Path,
  output: Option[Path],
  processLimit: Option[ProcessLimit],
  detectionConfig: DetectionConfig,
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
      detectionConfig = DetectionConfig.default,
      flags = ConfigFlags.default,
      parallelism = None,
    )

  lazy val declineOpts: Opts[Config] =
    (
      input,
      output,
      processLimit,
      DetectionConfig.declineOpts,
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
      .withDefault(Config.default.input)

  private lazy val output: Opts[Option[Path]] =
    Opts
      .option[String](
        long = "output",
        short = "o",
        help = "Output directory where to place the produced CSV files",
      )
      .map(Paths.get(_))
      .orNone

  private lazy val processLimit: Opts[Option[ProcessLimit]] =
    Opts
      .option[Int :| GreaterEqual[1]](
        long = "limit",
        short = "l",
        help = "The maximum amount of files to process",
      )
      .map(ProcessLimit.apply)
      .orNone

  private lazy val parallelism: Opts[Option[Parallelism]] =
    Opts
      .option[Int :| GreaterEqual[1]](
        long = "parallelism",
        help = "The number of concurrent threads used to process the files in batch. Default is: no limit",
      )
      .map(Parallelism.apply)
      .orNone

}
