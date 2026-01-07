package com.colofabrix.scala.beesight.files

import cats.effect.IO
import com.colofabrix.scala.beesight.IOConfig
import com.colofabrix.scala.beesight.model.FlightStagesPoints
import com.colofabrix.scala.beesight.model.FlysightPoint
import fs2.data.csv.*
import fs2.io.file.{ Files, Flags, Path as Fs2Path }
import java.nio.file.Path
import com.colofabrix.scala.beesight.model.OutputFlightPoint

/**
 * Aggregations of operations on files using fs2
 */
object CsvFileOps {

  /**
   * Reads a CSV file and decodes it into a stream of type A
   */
  def readCsv[A](filePath: Path)(using CsvRowDecoder[A, String]): fs2.Stream[IO, A] =
    Files[IO]
      .readAll(Fs2Path.fromNioPath(filePath), 1024, Flags.Read)
      .through(fs2.text.utf8.decode)
      .through(unixEol)
      .through(fs2.text.lines)
      .filter { line =>
        line.trim.nonEmpty && !line.trim.startsWith("#")
      }
      .intersperse("\n")
      .through(lenient.attemptDecodeUsingHeaders[A]())
      .collect {
        case Right(decoded) => decoded
      }

  /**
   * Returns a pipe that writes FlysightPoints to a CSV file
   */
  def writeCsvPipe[A](
    filePath: Path,
    dryRun: Boolean,
  )(using CsvRowEncoder[A, String],
  ): fs2.Pipe[IO, OutputFlightPoint[A], Nothing] =
    stream =>
      stream
        .through { s =>
          if !dryRun then s else fs2.Stream.empty
        }
        .map(_.source)
        .through(encodeUsingFirstHeaders(fullRows = true))
        .through(fs2.text.utf8.encode)
        .through(Files[IO].writeAll(Fs2Path.fromNioPath(filePath)))
        .as(())
        .drain

  private val unixEol: fs2.Pipe[IO, String, String] =
    _.map(_.replace("\r\n", "\n"))

}
