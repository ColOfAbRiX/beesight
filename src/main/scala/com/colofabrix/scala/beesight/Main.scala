package com.colofabrix.scala.beesight

import cats.effect.*
import cats.implicits.*
import cats.Show
import com.colofabrix.scala.beesight.Utils.*
import fs2.*
import os.*

object Main extends IOApp:

  private val ProcessedPostfix =
    "_processed"

  def run(args: List[String]): IO[ExitCode] =
    FileOps
      .findCsvFilesRecursively(Some(os.pwd / "resources"))
      .filterNot(_.toString.contains(ProcessedPostfix))
      .ioTapPrintln(inputPath => s"\nWorking on file '$inputPath'")
      .flatMap(analyzeFile)
      .compile
      .drain
      .as(ExitCode.Success)

  def analyzeFile(inputPath: Path): Stream[IO, ?] =
    val dirname / strBasename = inputPath: @unchecked
    val basename              = RelPath(strBasename)
    val backupFile            = dirname / (basename.baseName + ProcessedPostfix + "." + basename.ext)

    // Stream.io(os.copy(inputPath, backupFile, replaceExisting = false)) *>
      FileOps
      .readCsv[FlysightPoint](inputPath.toString)
      .through { data =>
        FlightStagesDetection
          .flightPoints(data)
          .through(FlightStagesDetection.cutData(data))
      }
      .through(FileOps.writeCsv(backupFile.toString))
