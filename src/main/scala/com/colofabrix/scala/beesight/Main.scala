package com.colofabrix.scala.beesight

import better.files.Dsl.*
import better.files.File
import cats.effect.*
import cats.implicits.*
import com.colofabrix.scala.beesight.StreamUtils.*
import com.colofabrix.scala.decline.IODeclineApp
import com.monovore.decline.Opts
import fs2.*

object Main extends IODeclineApp[Config]:

  val name: String =
    "beesight"

  val header: String =
    "Beesight - A Flysight data cleaner tool"

  val options: Opts[Config] =
    CliConfig.allOptions

  override def runWithConfig(config: Config): IO[ExitCode] =
    val main = Main(config)
    import main._

    FileOps
      .findCsvFilesRecursively(config.input)
      .through(removeOutputDirectory)
      .through(limitProcessing)
      .flatMap(processPath)
      .compile
      .drain
      .as(ExitCode.Success)

final class Main(config: Config):

  val removeOutputDirectory: Pipe[IO, File, File] =
    _.filterNot {
      _.toString.startsWith(config.output.toString)
    }

  val limitProcessing: Pipe[IO, File, File] =
    _.zipWithIndex.flatMap {
      case (path, _) if config.processLimit.isEmpty          => Stream.emit(path)
      case (path, i) if i < config.processLimit.getOrElse(0) => Stream.emit(path)
      case _                                                 => Stream.empty
    }

  def processPath(inputFile: File): Stream[IO, Unit] =
    val inputRelativePath = inputFile.toString.replace(config.input.toString, "").drop(1)
    val outputPath        = config.output.getOrElse(config.input / "processed")
    val outputFile        = outputPath / inputRelativePath

    Stream.ioPrintln(s"Processing file: $inputFile -> $outputFile") >>
    Stream.io(mkdirs(outputFile.parent)) >>
    processCsvFile(inputFile, outputFile) >>
    Stream.ioPrintln(s"DONE: $inputFile\n")

  def processCsvFile(inputFile: File, outputFile: File): Stream[IO, Unit] =
    if config.dryRun then
      Stream.empty
    else
      val flightStagesDetector = FlightStagesDetection(config)
      FileOps
        .readCsv[FlysightPoint](inputFile)
        .through { data =>
          flightStagesDetector
            .flightPoints(data)
            .through(flightStagesDetector.cutData(data))
        }
        .through(FileOps.writeCsv(outputFile))
