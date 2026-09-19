package com.colofabrix.scala.beesight

import cats.effect.IO
import com.colofabrix.scala.beesight.model.*
import fs2.data.csv.*
import java.io.FileInputStream
import java.nio.file.{ Path, Paths }
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipInputStream
import scala.io.Source

private[beesight] object TestFilesHandler {

  val testFilesDir: Path = Paths.get("src/test/resources/test_files")

  def countLines(path: Path): Int =
    val source =
      if isZipFile(path) then
        val zis = new ZipInputStream(new FileInputStream(path.toFile))
        zis.getNextEntry
        Source.fromInputStream(zis)
      else
        Source.fromFile(path.toFile)

    try {
      source
        .getLines()
        .drop(1)
        .count(line => line.trim.nonEmpty && !line.trim.startsWith("#"))
    } finally {
      source.close()
    }

  def readCsv[T](path: Path)(using CsvRowDecoder[T, String]): fs2.Stream[IO, T] =
    val rawLines =
      if isZipFile(path) then readLinesFromZip(path)
      else readLinesFromFile(path)

    rawLines
      .filter(line => line.trim.nonEmpty && !line.trim.startsWith("#"))
      .through(trimHeaderFields)
      .intersperse("\n")
      .through(lenient.attemptDecodeUsingHeaders[T]())
      .collect { case Right(value) => value }

  private def isZipFile(filePath: Path): Boolean =
    filePath.toString.toLowerCase.endsWith(".zip")

  private def readLinesFromZip(filePath: Path): fs2.Stream[IO, String] =
    val zis = new ZipInputStream(new FileInputStream(filePath.toFile))

    val lines =
      try {
        zis.getNextEntry
        Source.fromInputStream(zis).getLines().toList
      } finally {
        zis.close()
      }

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

  private given CellDecoder[OffsetDateTime] =
    CellDecoder[String].emap { value =>
      try {
        Right(OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME))
      } catch {
        case t: Exception => Left(new DecoderError(t.getMessage()))
      }
    }

  given CsvRowDecoder[CsvPoint, String] with
    def apply(row: CsvRow[String]): DecoderResult[CsvPoint] =
      for
        time <- row.as[OffsetDateTime]("time")
        hMSL <- row.as[Double]("hMSL")
        velN <- row.as[Double]("velN")
        velE <- row.as[Double]("velE")
        velD <- row.as[Double]("velD")
      yield CsvPoint(
        time = time.toInstant,
        altitude = hMSL,
        speed = GeoVector(
          north = velN,
          east = velE,
          vertical = velD
        ),
      )

}

/**
 * A raw flight data point decoded from the test CSV files, usable as detector input via its [[PointReader]].
 */
final private[beesight] case class CsvPoint(
  time: Instant,
  altitude: Double,
  speed: GeoVector,
)

private[beesight] object CsvPoint {

  given PointReader[CsvPoint] with {
    def time(point: CsvPoint): Instant    = point.time
    def altitude(point: CsvPoint): Double = point.altitude
    def speed(point: CsvPoint): GeoVector = point.speed
  }

}
