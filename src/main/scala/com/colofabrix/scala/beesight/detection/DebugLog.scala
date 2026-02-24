package com.colofabrix.scala.beesight.detection

import com.colofabrix.scala.beesight.collections.NEW
import com.colofabrix.scala.beesight.detection.model.DetectionData
import com.colofabrix.scala.beesight.model.{ FlightEventPoint, FlightEvents, GeoVector }

/**
 * Centralized debug logging for the detection package.
 *
 * Enabled via the BEESIGHT_DEBUG environment variable (case-insensitive "true").
 */
object DebugLog {

  private val enabled: Boolean =
    Option(System.getenv("BEESIGHT_DEBUG")).exists(_.equalsIgnoreCase("true"))

  /**
   * Prints a debug message. The message is lazily evaluated.
   */
  def apply(msg: => String): Unit =
    if enabled then println(msg)

  /**
   * Prints a titled section header followed by a lazily evaluated body.
   */
  def section(title: String)(body: => Unit): Unit =
    if enabled then
      println(s"\n${"═" * 60}")
      println(s"║ $title")
      println(s"${"═" * 60}")
      body

  /**
   * Formats a detection window as a concise summary string.
   */
  def windowSummary(window: NEW[DetectionData]): String =
    val indices = window.toVector.map(_.point.idx)
    s"[${indices.head}..${indices.last}] (${window.length} pts)"

  /**
   * Formats flight events as a concise summary string.
   */
  def eventsSummary(events: FlightEvents): String =
    val parts = Vector(
      events.takeoff.map(e => s"T@${e.index}"),
      events.freefall.map(e => s"F@${e.index}"),
      events.canopy.map(e => s"C@${e.index}"),
      events.landing.map(e => s"L@${e.index}"),
    ).flatten
    if parts.isEmpty then "∅" else parts.mkString(",")

  //  Double Formatting  //

  extension (d: Double) {

    /** Rounds a Double to 3 decimal places for display. */
    def fmt: String = f"$d%.3f"

  }

  extension (v: GeoVector) {

    /** Formats a GeoVector with 3 decimal places. */
    def fmt: String = s"(n=${v.north.fmt}, e=${v.east.fmt}, v=${v.vertical.fmt})"

  }

}
