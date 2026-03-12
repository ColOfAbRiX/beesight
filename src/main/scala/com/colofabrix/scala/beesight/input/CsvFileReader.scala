package com.colofabrix.scala.beesight.input

import com.colofabrix.scala.beesight.IOConfig
import de.lhns.fs2.compress.ZipUnarchiver
import fs2.data.csv.*
import fs2.io.file.{ Files, Flags, Path as Fs2Path }
import java.nio.file.Path

/**
 * Aggregations of operations on files using fs2
 */
object CsvFileReader {

  private given ZipUnarchiver[IOConfig] =
    ZipUnarchiver.make[IOConfig]()

  /**
   * Reads a CSV file and decodes it into a stream of type A. Automatically detects and reads from zip files when the
   * path ends with .zip
   */
  def readCsv[A](filePath: Path)(using CsvRowDecoder[A, String]): fs2.Stream[IOConfig, A] =
    val rawLines =
      if isZipFile(filePath) then readLinesFromZip(filePath)
      else readLinesFromFile(filePath)

    rawLines
      .filter { line =>
        line.trim.nonEmpty && !line.trim.startsWith("#")
      }
      .through(trimHeaders)
      .intersperse("\n")
      .through(lenient.attemptDecodeUsingHeaders[A]())
      .collect { case Right(decoded) => decoded }

  private def isZipFile(filePath: Path): Boolean =
    filePath
      .toString
      .toLowerCase
      .endsWith(".zip")

  private def readLinesFromFile(filePath: Path): fs2.Stream[IOConfig, String] =
    Files[IOConfig]
      .readAll(Fs2Path.fromNioPath(filePath), 1024, Flags.Read)
      .through(fs2.text.utf8.decode)
      .through(unixEol)
      .through(fs2.text.lines)

  private def readLinesFromZip(filePath: Path): fs2.Stream[IOConfig, String] =
    Files[IOConfig]
      .readAll(Fs2Path.fromNioPath(filePath), 1024, Flags.Read)
      .through(ZipUnarchiver[IOConfig].unarchive)
      .head
      .flatMap(_._2)
      .through(fs2.text.utf8.decode)
      .through(unixEol)
      .through(fs2.text.lines)

  private val trimHeaders: fs2.Pipe[IOConfig, String, String] =
    stream =>
      stream.zipWithIndex.flatMap {
        case (line, 0) => // Header row: fix empty first column name
          val fields = line.split(",", -1).map(_.trim)
          if fields.nonEmpty && fields(0).isEmpty then fields(0) = "time"
          fs2.Stream.emit(fields.mkString(","))
        case (_, 1) => // Units row: skip it
          fs2.Stream.empty
        case (line, _) => // Data rows: pass through
          fs2.Stream.emit(line)
      }

  private val unixEol: fs2.Pipe[IOConfig, String, String] =
    _.map(_.replace("\r\n", "\n"))

}
