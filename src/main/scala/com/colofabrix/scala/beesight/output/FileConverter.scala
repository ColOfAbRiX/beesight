package com.colofabrix.scala.beesight.output

import cats.effect.IO
import cats.syntax.all.*
import com.colofabrix.scala.beesight.detection.jumpevents.*
import com.colofabrix.scala.beesight.detection.readers.*
import com.colofabrix.scala.beesight.formats.*
import com.colofabrix.scala.beesight.formats.convert.toFlySight
import com.colofabrix.scala.beesight.formats.devices.*
import com.colofabrix.scala.beesight.input.*
import com.colofabrix.scala.beesight.IOConfig
import com.colofabrix.scala.beesight.output.*
import fs2.data.csv.*
import fs2.io.file.{ Files, Path as Fs2Path }
import java.nio.file.*

object FileConverter {

  def conversionPipeline[A](
    csvStream: fs2.Stream[IOConfig, A],
    ipnutCsvFile: Path,
    dryRun: Boolean,
  )(using
    FlysightReader[A],
    CsvRowEncoder[FlysightPoint, String],
  ): fs2.Stream[IOConfig, Unit] =
    if dryRun then
      csvStream.drain
    else
      val ouptutCsvPath = Fs2Path.fromNioPath(convertedFilePath(ipnutCsvFile))
      csvStream
        .map(_.toFlySight())
        .through(encodeUsingFirstHeaders(fullRows = true))
        .through(fs2.text.utf8.encode)
        .through(Files[IOConfig].writeAll(ouptutCsvPath))
        .drain

  private def convertedFilePath(processedFile: Path): Path =
    val baseName =
      processedFile
        .getFileName
        .toString
        .replaceFirst("\\.[^.]+$", "")

    processedFile.resolveSibling(s"$baseName.flysight.csv")

}
