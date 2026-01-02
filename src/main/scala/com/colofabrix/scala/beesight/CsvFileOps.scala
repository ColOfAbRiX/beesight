package com.colofabrix.scala.beesight

import cats.effect.IO
import fs2.*
import fs2.data.csv.*
import fs2.io.file.{Files, Flags, Path as Fs2Path}
import java.nio.file.Path

/**
 * Aggregations of operations on files using fs2
 */
object CsvFileOps {

  /**
   * Reads a CSV file and decodes it into a stream of type A
   */
  def readCsv[A](filePath: Path)(using CsvRowDecoder[A, String]): Stream[IO, A] =
    Files[IO]
      .readAll(Fs2Path.fromNioPath(filePath), 1024, Flags.Read)
      .through(fs2.text.utf8.decode)
      .through(unixEol)
      .through(text.lines)
      .filter(line => line.trim.nonEmpty && !line.trim.startsWith("#"))
      .intersperse("\n")
      .through(lenient.attemptDecodeUsingHeaders[A]())
      .collect { case Right(decoded) => decoded }

  /**
   * Writes a stream of type A to a CSV file
   */
  def writeCsv[A](filePath: Path)(using CsvRowEncoder[A, String]): Pipe[IO, A, Unit] =
    data =>
      data
        .through(encodeUsingFirstHeaders(fullRows = true))
        .through(text.utf8.encode)
        .through(Files[IO].writeAll(Fs2Path.fromNioPath(filePath)))
        .as(())

  private val unixEol: Pipe[IO, String, String] =
    _.map(_.replace("\r\n", "\n"))

}
