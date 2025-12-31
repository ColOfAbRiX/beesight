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

/**
 * Aggregations of operations on files
 */
object FileOps:

  def readCsv[A](filePath: File)(using CsvRowDecoder[A, String]): Stream[IO, A] =
    Files[IO]
      .readAll(filePath, 1024, Flags.Read)
      .through(fs2.text.utf8.decode)
      .through(unixEol)
      .through(text.lines)
      .filter(line => line.trim.nonEmpty && !line.trim.startsWith("#"))
      .intersperse("\n")
      .through(lenient.attemptDecodeUsingHeaders[A]())
      .collect { case Right(decoded) => decoded }

  def writeCsv[A](filePath: File)(using CsvRowEncoder[A, String]): Pipe[IO, A, Unit] =
    data =>
      data
        .through(encodeUsingFirstHeaders(fullRows = true))
        .through(text.utf8.encode)
        .through(Files[IO].writeAll(filePath))
        .as(())

  def findCsvFilesRecursively(inputPath: File): Stream[IO, File] =
    if inputPath.isRegularFile then
      if inputPath.extension(toLowerCase = true) === Some(".csv") then
        Stream.emit(inputPath)
      else
        Stream.empty
    else
      Files[IO]
        .list(inputPath)
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
