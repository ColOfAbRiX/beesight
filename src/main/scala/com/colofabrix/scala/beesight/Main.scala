package com.colofabrix.scala.beesight

import better.files.File
import better.files.Dsl.*
import cats.effect.*
import cats.implicits.*
import com.colofabrix.scala.beesight.StreamUtils.*
import fs2.*
import com.monovore.decline.CommandApp
import com.monovore.decline.Opts

object Main extends IODeclineApp[Config]:

  val name: String =
    "beesight"

  val options: Opts[Config] =
    Config.allOptions

  val header: String =
    "Beesight - A clean Flysight data tool"

  override def runWithConfig(config: Config): IO[ExitCode] =
    FileOps
      .findCsvFilesRecursively(config.input)
      .filterNot {
        _.toString.startsWith(config.output.toString)
      }
      .take(2)
      .flatMap(processCsvFile(config))
      .compile
      .drain
      .as(ExitCode.Success)

  def processCsvFile(config: Config)(inputPath: File): Stream[IO, Nothing] =
    val inputRelativePath = inputPath.toString.replace(config.input.toString, "").drop(1)
    val outputFile        = config.output / inputRelativePath

    Stream.io(mkdirs(outputFile.parent)) *>
    Stream.ioPrintln(s"Processing file $inputRelativePath") *>
    FileOps
      .readCsv[FlysightPoint](inputPath)
      .through { data =>
        FlightStagesDetection
          .flightPoints(data)
          .through(FlightStagesDetection.cutData(data))
      }
      .through(FileOps.writeCsv(outputFile))
