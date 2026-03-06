package com.colofabrix.scala.beesight.output

import cats.effect.IO
import com.colofabrix.scala.beesight.IOConfig
import com.colofabrix.scala.beesight.config.Config
import com.colofabrix.scala.beesight.model.formats.FlysightPoint
import com.colofabrix.scala.beesight.model.{ FlightEvents, OutputFlightPoint }
import com.colofabrix.scala.beesight.utils.*
import de.lhns.fs2.compress.ZipUnarchiver
import fs2.data.csv.*
import fs2.io.file.{ Files, Flags, Path as Fs2Path }
import java.nio.file.Path

/**
 * Aggregations of operations on files using fs2
 */
final class CsvResultWriter private (config: Config) {

  /**
   * Returns a pipe that writes OutputFlightPoint to a CSV file (includes phase column)
   */
  def writePipe[A](
    filePath: Path,
  )(using
    CsvRowEncoder[OutputFlightPoint[A], String],
  ): fs2.Pipe[IOConfig, OutputFlightPoint[A], Nothing] =
    stream =>
      stream
        .through(encodeUsingFirstHeaders(fullRows = true))
        .through(fs2.text.utf8.encode)
        .through(Files[IOConfig].writeAll(Fs2Path.fromNioPath(filePath)))
        .drainWhen(!config.dryRun && config.writeCsv)

}

object CsvResultWriter {

  def apply(config: Config): CsvResultWriter =
    new CsvResultWriter(config)

}
