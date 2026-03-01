package com.colofabrix.scala.beesight.input.formats

import cats.syntax.all.*
import com.colofabrix.scala.beesight.*
import com.colofabrix.scala.beesight.input.CsvFileReader
import com.colofabrix.scala.beesight.model.FileFormat
import com.colofabrix.scala.beesight.model.formats.{ AirlogPoint, FlysightPoint }
import java.nio.file.Path

/**
 * Detects the file format of a CSV file by inspecting its header columns.
 */
private[beesight] object FormatDetector {

  /**
   * Detects the format of a CSV file by reading its header line and checking whether all required columns for a known
   * format are present
   */
  def detect(filePath: Path): IOConfig[FileFormat] =
    CsvFileReader
      .readHeaderLine(filePath)
      .flatMap { headerLine =>
        val columns = headerLine.split(",").map(_.trim).toSet

        if FlysightPoint.requiredColumns.subsetOf(columns) then
          FileFormat.FlySight.pure[IOConfig]
        else if AirlogPoint.requiredColumns.subsetOf(columns) then
          FileFormat.Airlog.pure[IOConfig]
        else
          val msg = s"Unknown CSV format in '$filePath'. Header columns: ${columns.mkString(", ")}"
          new IllegalArgumentException(msg).raiseError[IOConfig, FileFormat]
      }

}
