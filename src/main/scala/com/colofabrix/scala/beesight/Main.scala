package com.colofabrix.scala.beesight

import cats.effect.*
import cats.implicits.*
import fs2.data.csv.*
import fs2.io.file.{ Files, Flags, Path }
import fs2.Stream

object Main extends IOApp:

  def run(args: List[String]): IO[ExitCode] =
    analyzeFile("resources/sample.csv")
      .compile
      .drain
      .as(ExitCode.Success)

  def analyzeFile(filePath: String): Stream[IO, Unit] =
    readCsv(filePath).as(())

  def readCsv(filePath: String): Stream[IO, FlysightPoint] =
    Files[IO]
      .readAll(Path(filePath), 1024, Flags.Read)
      .through(fs2.text.utf8.decode)
      .through(lenient.attemptDecodeUsingHeaders[FlysightPoint]())
      .collect { case Right(decoded) => decoded }
