package com.colofabrix.scala.beesight.detection

import cats.effect.IO
import com.colofabrix.scala.beesight.model.*
import fs2.data.csv.*
import java.io.FileInputStream
import java.nio.file.Path
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipInputStream
import scala.io.Source

/**
 * Test-only reader that parses Flysight CSV/ZIP files directly into IndexedDataPoint.
 * Independent of the CLI's CsvFileReader and format adapters.
 */
private[detection] object FlysightTestReader {

  private given CellDecoder[OffsetDateTime] =
    CellDecoder[String].emap { value =>
      try Right(OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME))
      catch case t: Exception => Left(new DecoderError(t.getMessage()))
    }

  private given CsvRowDecoder[IndexedDataPoint, String] with
    def apply(row: CsvRow[String]): DecoderResult[IndexedDataPoint] =
      for
        time <- row.as[OffsetDateTime]("time")
        hMSL <- row.as[Double]("hMSL")
        velN <- row.as[Double]("velN")
        velE <- row.as[Double]("velE")
        velD <- row.as[Double]("velD")
      yield IndexedDataPoint(
        time = time.toInstant,
        idx = 0,
        altitude = hMSL,
        speed = GeoVector(north = velN, east = velE, vertical = velD),
      )

  /**
   * Reads a Flysight CSV or ZIP file and returns a stream of IndexedDataPoint.
   * Handles the units row by skipping the first decode failure.
   */
  def readCsvStream(filePath: Path): fs2.Stream[IO, IndexedDataPoint] =
    val rawLines =
      if isZipFile(filePath) then readLinesFromZip(filePath)
      else readLinesFromFile(filePath)

    rawLines
      .filter(line => line.trim.nonEmpty && !line.trim.startsWith("#"))
      .through(trimHeaderFields)
      .intersperse("\n")
      .through(lenient.attemptDecodeUsingHeaders[IndexedDataPoint]())
      .zipWithIndex
      .flatMap {
        case (Right(a), _) =>
          fs2.Stream.emit(a)
        case (Left(_), idx) if idx < 1 =>
          fs2.Stream.empty
        case (Left(err), _) =>
          fs2.Stream.raiseError[IO](new RuntimeException(s"Failed to decode row: $err"))
      }

  /**
   * Counts the number of data rows in a Flysight CSV or ZIP file (excluding header).
   */
  def countCsvLines(filePath: Path): Int =
    val source =
      if isZipFile(filePath) then
        val zis = new ZipInputStream(new FileInputStream(filePath.toFile))
        zis.getNextEntry
        Source.fromInputStream(zis)
      else
        Source.fromFile(filePath.toFile)

    try
      source
        .getLines()
        .drop(1)
        .count(line => line.trim.nonEmpty && !line.trim.startsWith("#"))
    finally
      source.close()

  private def isZipFile(filePath: Path): Boolean =
    filePath.toString.toLowerCase.endsWith(".zip")

  private def readLinesFromZip(filePath: Path): fs2.Stream[IO, String] =
    val lines =
      val zis = new ZipInputStream(new FileInputStream(filePath.toFile))
      try
        zis.getNextEntry
        Source.fromInputStream(zis).getLines().toList
      finally
        zis.close()
    fs2.Stream.emits(lines)

  private def readLinesFromFile(filePath: Path): fs2.Stream[IO, String] =
    val lines =
      val source = Source.fromFile(filePath.toFile)
      try source.getLines().toList
      finally source.close()
    fs2.Stream.emits(lines)

  private val trimHeaderFields: fs2.Pipe[IO, String, String] =
    stream =>
      stream
        .zipWithIndex
        .map {
          case (line, 0) => line.split(",", -1).map(_.trim).mkString(",")
          case (line, _) => line
        }

}
