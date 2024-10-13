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

  val options: Opts[Config] =
    Config.allOptions

  val header: String =
    "Beesight - A Flysight data cleaner tool"

  override def runWithConfig(config: Config): IO[ExitCode] =
    FileOps
      .findCsvFilesRecursively(config.input)
      .through(removeOutputDirectory(config))
      .through(limitProcessing(config))
      .flatMap(processPath(config))
      .compile
      .drain
      .as(ExitCode.Success)

  def removeOutputDirectory(config: Config): Pipe[IO, File, File] =
    _.filterNot {
      _.toString.startsWith(config.output.toString)
    }

  def limitProcessing(config: Config): Pipe[IO, File, File] =
    _.zipWithIndex.flatMap {
      case (path, _) if config.processLimit.isEmpty          => Stream.emit(path)
      case (path, i) if i < config.processLimit.getOrElse(0) => Stream.emit(path)
      case _                                                 => Stream.empty
    }

  def processPath(config: Config)(inputFile: File): Stream[IO, Unit] =
    val inputRelativePath = inputFile.toString.replace(config.input.toString, "").drop(1)
    val outputPath        = config.output.getOrElse(config.input / "processed")
    val outputFile        = outputPath / inputRelativePath

    Stream.ioPrintln(s"Processing file: $inputFile -> $outputFile") >>
    Stream.io(mkdirs(outputFile.parent)) >> {
      if config.dryRun then
        Stream.empty
      else
        processCsvFile(inputFile, outputFile)
    } >>
    Stream.eval(IO(println(s"DONE: $inputFile\n")))

  def processCsvFile(inputFile: File, outputFile: File): Stream[IO, Unit] =
    FileOps
      .readCsv[FlysightPoint](inputFile)
      .through { data =>
        FlightStagesDetection
          .flightPoints(data)
          .through(FlightStagesDetection.cutData(data))
      }
      .through(FileOps.writeCsv(outputFile))
      .drain
