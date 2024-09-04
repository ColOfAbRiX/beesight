package com.colofabrix.scala.beesight

import cats.effect.*
import cats.implicits.*
import cats.Show
import com.colofabrix.scala.beesight.Utils.*
import fs2.*
import os.*

object Main extends IOApp:

  def run(args: List[String]): IO[ExitCode] =
    FileOps
      .findCsvFilesRecursively(Some(os.pwd / "resources"))
      .take(1)
      .filterNot(_.toString.contains("_original"))
      .ioTapPrintln(inputPath => s"Working on file '$inputPath'")
      .flatMap { csvFilePath =>
        analyzeFile(csvFilePath, "resources/output.csv")
      }
      .compile
      .drain
      .as(ExitCode.Success)

  def analyzeFile(inputPath: Path, outputPath: String): Stream[IO, ?] =
    val dirname / strBasename = inputPath: @unchecked
    val basename              = RelPath(strBasename)
    val backupFile            = dirname / (basename.baseName + "_original" + "." + basename.ext)

    Stream.io(os.copy(inputPath, backupFile, replaceExisting = false)) *>
    FileOps
      .readCsv[FlysightPoint](inputPath.toString)
      .through(FlightStagesDetection.cutoffData)
      .through(FileOps.writeCsv(outputPath))

