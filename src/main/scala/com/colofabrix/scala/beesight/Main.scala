package com.colofabrix.scala.beesight

import cats.effect.*
import cats.implicits.*
import cats.Show
import com.colofabrix.scala.beesight.Utils.*
import fs2.*
import os.*

object Main extends IOApp:

  private val ProcessedDirname =
    "processed"

  def run(args: List[String]): IO[ExitCode] =
    FileOps
      .findCsvFilesRecursively(Some(os.pwd / "resources"))
      .filterNot {
        _.toString.contains(ProcessedDirname)
      }
      .ioTapPrintln { inputPath =>
        s"\nProcessing file '$inputPath'"
      }
      .flatMap(analyzeFile)
      .compile
      .drain
      .as(ExitCode.Success)

  def analyzeFile(inputPath: Path): Stream[IO, ?] =
    val dirname / strBasename = inputPath: @unchecked
    val basename              = RelPath(strBasename)
    val outputFile            = dirname / ProcessedDirname / (basename.baseName + "." + basename.ext)

    Stream.io(os.makeDir.all(dirname / ProcessedDirname)) *>
    FileOps
      .readCsv[FlysightPoint](inputPath.toString)
      .through { data =>
        FlightStagesDetection
          .flightPoints(data)
          .through(FlightStagesDetection.cutData(data))
      }
      .through(FileOps.writeCsv(outputFile.toString))
