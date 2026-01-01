package com.colofabrix.scala.beesight

import better.files.Dsl.*
import better.files.File
import cats.effect.*
import cats.implicits.*
import com.colofabrix.scala.beesight.StreamUtils.*
import com.colofabrix.scala.beesight.config.*
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.declinio.IODeclineApp
import com.monovore.decline.Opts
import fs2.*

object Main extends IODeclineApp[Config] {

  val name: String =
    "beesight"

  val header: String =
    "Beesight - A Flysight data cleaner tool"

  val options: Opts[Config] =
    CliConfig.allOptions

  override def runWithConfig(config: Config): IO[ExitCode] =
    Main(config).start

}

final class Main(config: Config) {

  lazy val start =
    FileOps
      .findCsvFilesRecursively(config.input)
      .through(removeOutputDirectory)
      .through(limitProcessing)
      .flatMap(processPath)
      .compile
      .drain
      .as(ExitCode.Success)

  lazy val removeOutputDirectory: Pipe[IO, File, File] =
    _.filterNot {
      _.toString.startsWith(config.output.toString)
    }

  lazy val limitProcessing: Pipe[IO, File, File] =
    _.zipWithIndex.flatMap {
      case (path, _) if config.processLimit.isEmpty          => Stream.emit(path)
      case (path, i) if i < config.processLimit.getOrElse(0) => Stream.emit(path)
      case _                                                 => Stream.empty
    }

  def processPath(inputFile: File): Stream[IO, Unit] =
    for {
      outputFile <- Stream.emit(buildOutputFileName(inputFile))
      _          <- fs2Println(s"Processing file: $inputFile -> $outputFile")
      _          <- fs2Io(mkdirs(outputFile.parent))
      _          <- processCsvFile(inputFile, outputFile)
      _          <- fs2Println(s"DONE: $inputFile\n")
    } yield ()

  def buildOutputFileName(inputFile: File): File =
    val outputRoot =
      config.output.getOrElse {
        if config.input.isDirectory then config.input / "processed"
        else config.input.parent / "processed"
      }

    val relativePath =
      if config.input.isDirectory then
        config.input.path.toAbsolutePath().relativize(inputFile.path.toAbsolutePath()).toString
      else
        inputFile.name

    outputRoot / relativePath

  def processCsvFile(inputFile: File, outputFile: File): Stream[IO, Unit] =
    if config.dryRun then
      Stream.empty
    else
      val stagesDetector = FlightStagesDetection(config)
      val dataCutter     = DataCutter(config)

      val csvStream = FileOps.readCsv[FlysightPoint](inputFile)

      Stream
        .eval(stagesDetector.detect(csvStream))
        .flatMap(flightPoints => csvStream.through(dataCutter.cut(flightPoints)))
        .through(FileOps.writeCsv(outputFile))

}
