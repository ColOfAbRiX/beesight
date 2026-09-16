package com.colofabrix.scala.beesight.model

import cats.syntax.all.*
import com.colofabrix.scala.beesight.*
import com.colofabrix.scala.beesight.model.FileFormat
import com.colofabrix.scala.beesight.model.formats.*

/**
 * Detects the file format of a CSV file by inspecting its header columns.
 */
private[beesight] object FormatDetector {

  def detect(headerLine: String): Option[FileFormat] =
    val headers =
      headerLine
        .split(",")
        .map(_.trim)
        .toSet

    detect(headers)

  def detect(headers: Set[String]): Option[FileFormat] =
    if FlysightPoint.requiredColumns.subsetOf(headers) then
      Some(FileFormat.FlySight)
    else if AirlogPoint.requiredColumns.subsetOf(headers) then
      Some(FileFormat.Airlog)
    else if DekunuPoint.requiredColumns.subsetOf(headers) then
      Some(FileFormat.Dekunu)
    else
      None

}
