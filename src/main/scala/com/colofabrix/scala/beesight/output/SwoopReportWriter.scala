package com.colofabrix.scala.beesight.output

import com.colofabrix.scala.beesight.detection.model.SwoopData
import com.colofabrix.scala.beesight.metadata.StoredConversions.*
import io.circe.syntax.*

/**
 * Renders the swoop landing analysis as the JSON swoop report.
 */
object SwoopReportWriter {

  /** Renders the swoop analysis as a JSON document */
  def toJson(swoop: SwoopData): String =
    swoop
      .toStored
      .asJson
      .spaces2SortKeys

}
