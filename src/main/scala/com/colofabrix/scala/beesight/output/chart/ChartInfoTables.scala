package com.colofabrix.scala.beesight.output.chart

import com.colofabrix.scala.beesight.detection.model.*

/**
 * Renders the HTML tables that summarise the detected flight stages and the swoop analysis.
 */
private[chart] object ChartInfoTables {

  def stagesTableJs(stages: FlightEvents): String =
    val rows = List(
      "Takeoff" -> stages.takeoff,
      "Freefall" -> stages.freefall,
      "Canopy" -> stages.canopy,
      "Landing" -> stages.landing,
    )
    tableJs("Detected Flight Stages", rows, "")

  def swoopTableJs(swoop: Option[SwoopData]): String =
    swoop.fold("") { data =>
      val rows = List[(String, Option[EventPoint])](
        "Pattern entry" -> Some(data.patternEntry),
        "Dive point" -> Some(data.divePoint),
        "Turn initiation" -> Some(data.turnInitiation),
        "Rollout start" -> Some(data.rolloutStart),
        "Bottom of turn" -> Some(data.bottomOfTurn),
        "Stop" -> Some(data.stop),
      )
      val rotation =
        s"""      <tr><td>Degrees of rotation</td><td colspan="2">${formatAltitude(data.degreesOfRotation.value)}° ${data.turnDirection.productPrefix}</td></tr>"""

      tableJs("Swoop Analysis", rows, rotation)
    }

  private def tableJs(caption: String, rows: List[(String, Option[EventPoint])], extraRows: String): String =
    val body = rows.map {
      case (event, Some(point)) =>
        s"""      <tr><td>$event</td><td>${point.index}</td><td>${formatAltitude(point.altitude)}</td></tr>"""
      case (event, None) =>
        s"""      <tr><td>$event</td><td colspan="2">Not detected</td></tr>"""
    }.mkString("\n")

    val extras = if extraRows.isEmpty then "" else "\n" + extraRows

    s"""    <h3>$caption</h3>
       |    <table>
       |      <thead><tr><th>Event</th><th>Point</th><th>Altitude (m)</th></tr></thead>
       |      <tbody>
       |$body$extras
       |      </tbody>
       |    </table>""".stripMargin

  private def formatAltitude(value: Double): String =
    "%,.2f".formatLocal(java.util.Locale.UK, value)

}
