package com.colofabrix.scala.beesight.output

import com.colofabrix.scala.beesight.IOConfig
import com.colofabrix.scala.beesight.config.Config
import fs2.data.csv.*
import fs2.io.file.{ Files, Path as Fs2Path }
import java.nio.file.Path

/**
 * Writes processed flight data with detected phases to CSV files.
 */
final private[beesight] class CsvResultWriter private (config: Config) {

  /**
   * Returns a pipe that writes OutputFlightPoint to a CSV file (includes phase column)
   */
  def writePipe[A](filePath: Path)(using CsvRowEncoder[A, String]): fs2.Pipe[IOConfig, (?, A), Nothing] =
    stream =>
      if !config.flags.dryRun && config.flags.writeCsv then
        stream
          .map(_._2)
          .through(encodeUsingFirstHeaders(fullRows = true))
          .through(fs2.text.utf8.encode)
          .through(Files[IOConfig].writeAll(Fs2Path.fromNioPath(filePath)))
          .drain
      else
        stream.drain

}

private[beesight] object CsvResultWriter {

  def apply(config: Config): CsvResultWriter =
    new CsvResultWriter(config)

}
