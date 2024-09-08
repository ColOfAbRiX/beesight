package com.colofabrix.scala.beesight

import cats.effect.implicits.*
import cats.effect.IO
import cats.implicits.*
import fs2.*
import fs2.data.csv.*
import fs2.io.file.{ Files, Flags, Path }

object FileOps:

  def readCsv[A](filePath: String)(using CsvRowDecoder[A, String]): Stream[IO, A] =
    Files[IO]
      .readAll(Path(filePath), 1024, Flags.Read)
      .through(fs2.text.utf8.decode)
      .through(unixEol)
      .through(lenient.attemptDecodeUsingHeaders[A]())
      .collect { case Right(decoded) => decoded }

  def writeCsv[A](filePath: String)(using CsvRowEncoder[A, String]): Pipe[IO, A, Nothing] =
    data =>
      data
        .through(encodeUsingFirstHeaders(fullRows = true))
        .through(text.utf8.encode)
        .through(Files[IO].writeAll(Path(filePath)))

  def findCsvFilesRecursively(wd: Option[os.Path]): Stream[IO, os.Path] =
    Stream
      .eval {
        IO(os.list(wd.getOrElse(os.pwd)).iterator)
      }
      .flatMap {
        Stream.fromIterator[IO](_, chunkSize = 1)
      }
      .flatMap {
        case path if os.isDir(path)                   => findCsvFilesRecursively(Some(path))
        case path if path.ext.toLowerCase() === "csv" => Stream.emit(path)
        case _                                        => Stream.empty
      }

  val unixEol: Pipe[IO, String, String] =
    _.map(_.replace("\r\n", "\n"))
