package com.colofabrix.scala.beesight

import better.files.*
import better.files.File.*
import cats.effect.implicits.*
import cats.effect.IO
import cats.implicits.*
import com.colofabrix.scala.beesight.FileUtils.given
import fs2.*
import fs2.data.csv.*
import fs2.io.file.{ Files, Flags }

object FileOps:

  def readCsv[A](filePath: File)(using CsvRowDecoder[A, String]): Stream[IO, A] =
    Files[IO]
      .readAll(filePath, 1024, Flags.Read)
      .through(fs2.text.utf8.decode)
      .through(unixEol)
      .through(lenient.attemptDecodeUsingHeaders[A]())
      .collect { case Right(decoded) => decoded }

  def writeCsv[A](filePath: File)(using CsvRowEncoder[A, String]): Pipe[IO, A, Nothing] =
    data =>
      data
        .through(encodeUsingFirstHeaders(fullRows = true))
        .through(text.utf8.encode)
        .through(Files[IO].writeAll(filePath))

  def findCsvFilesRecursively(directory: File): Stream[IO, File] =
    Files[IO]
      .list(directory)
      .map(fs2ToBf)
      .flatMap {
        case path if path.isDirectory =>
          findCsvFilesRecursively(path)
        case path if path.extension(toLowerCase = true) === Some(".csv") =>
          Stream.emit(path.absolute)
        case path =>
          Stream.empty
      }

  val unixEol: Pipe[IO, String, String] =
    _.map(_.replace("\r\n", "\n"))
