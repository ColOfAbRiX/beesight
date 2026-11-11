package com.colofabrix.scala.beesight.output
import com.colofabrix.scala.beesight.model.WorkingPaths

import com.colofabrix.scala.beesight.*
import com.colofabrix.scala.beesight.config.Config
import com.colofabrix.scala.beesight.detection.readers.FlysightReader
import com.colofabrix.scala.beesight.formats.convert.toFlySight
import com.colofabrix.scala.beesight.formats.devices.FlysightPoint
import com.colofabrix.scala.beesight.input.CsvFileReader
import com.colofabrix.scala.beesight.IOConfig
import fs2.data.csv.*
import fs2.io.file.{ Files, Path as Fs2Path }
import java.nio.file.Path

/**
 * Converts flight data files to the FlySight CSV format.
 */
object FileConverter {

  def convertFile[A: FlysightReader: CsvRowDecoder[*, String]](paths: WorkingPaths): IOConfig[Unit] =
    IOConfig
      .ask
      .flatMap { config =>
        if config.global.dryRun then
          IOConfig.unit
        else
          CsvFileReader
            .readCsv[A](paths.inputFile)
            .map(_.toFlySight())
            .through(encodeUsingFirstHeaders(fullRows = true))
            .through(fs2.text.utf8.encode)
            .through(Files[IOConfig].writeAll(Fs2Path.fromNioPath(paths.flysightCsv)))
            .compile
            .drain
      }

}
