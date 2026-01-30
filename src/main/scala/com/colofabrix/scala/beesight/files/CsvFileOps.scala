package com.colofabrix.scala.beesight.files

import cats.effect.IO
import com.colofabrix.scala.beesight.IOConfig
import com.colofabrix.scala.beesight.model.FlightEvents
import com.colofabrix.scala.beesight.model.formats.FlysightPoint
import fs2.data.csv.*
import fs2.io.file.{ Files, Flags, Path as Fs2Path }
import java.nio.file.Path
import com.colofabrix.scala.beesight.model.OutputFlightRow

/**
 * Aggregations of operations on files using fs2
 */
object CsvFileOps {

  /**
   * Reads a CSV file and decodes it into a stream of type A
   */
  def readCsv[A](filePath: Path)(using CsvRowDecoder[A, String]): fs2.Stream[IOConfig, A] =
    Files[IOConfig]
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
   * Returns a pipe that writes OutputFlightRow to a CSV file (includes phase column)
   */
  def writeCsvPipe[A](
    filePath: Path,
    dryRun: Boolean,
  )(using CsvRowEncoder[OutputFlightRow[A], String],
  ): fs2.Pipe[IOConfig, OutputFlightRow[A], Nothing] =
    stream =>
      stream
        .through { s =>
          if !dryRun then s else fs2.Stream.empty
        }
        .through(encodeUsingFirstHeaders(fullRows = true))
        .through(fs2.text.utf8.encode)
        .through(Files[IOConfig].writeAll(Fs2Path.fromNioPath(filePath)))
        .as(())
        .drain

  private val unixEol: fs2.Pipe[IOConfig, String, String] =
    _.map(_.replace("\r\n", "\n"))

}
