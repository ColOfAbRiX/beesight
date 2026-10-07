package com.colofabrix.scala.beesight.formats

import com.colofabrix.scala.beesight.*
import com.colofabrix.scala.beesight.formats.devices.{ AirlogPoint, DekunuPoint, FlysightPoint, GenericPoint }

/**
 * Detects the file format of a CSV file by inspecting its header columns.
 */
private[beesight] object FormatDetector {

  private val allFormatColumns =
    List(
      FileFormat.FlySight -> FlysightPoint.requiredColumns,
      FileFormat.Airlog   -> AirlogPoint.requiredColumns,
      FileFormat.Dekunu   -> DekunuPoint.requiredColumns,
      FileFormat.Generic  -> GenericPoint.requiredColumns
    )

  def detect(headerLine: String): Option[FileFormat] =
    val headers =
      headerLine
        .split(",")
        .map(_.trim)
        .toSet

    detect(headers)

  def detect(headers: Set[String]): Option[FileFormat] =
    allFormatColumns
      .filter {
        // This is used to ensure at compile time that we don't miss any new format
        case (FileFormat.FlySight, _) => true
        case (FileFormat.Airlog, _)   => true
        case (FileFormat.Dekunu, _)   => true
        case (FileFormat.Generic, _)  => true
      }
      .map { (format, columns) => (format, columns.size, headers.intersect(columns).size) }
      .filter { (_, required, present) => present >= required }
      .sortBy { (_, _, present) => present }
      .lastOption
      .map(_._1)

}
