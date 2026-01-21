package com.colofabrix.scala.beesight.model
import com.colofabrix.scala.beesight.model.formats.FlysightPoint

/**
 * Input data point for flight stage detection
 *
 * @param time The timestamp of the data point
 * @param altitude Altitude in meters above sea level
 * @param northSpeed Velocity north in m/s
 * @param eastSpeed Velocity east in m/s
 * @param verticalSpeed Velocity down in m/s (positive = descending)
 * @param source The original source data point
 */
final case class InputFlightRow[A](
  time: java.time.Instant,
  altitude: Double,
  northSpeed: Double,
  eastSpeed: Double,
  verticalSpeed: Double,
  source: A,
)

object InputFlightRow {

  /**
   * Creates an InputFlightPoint from a FlysightPoint
   */
  def fromFlysightPoint(p: FlysightPoint): InputFlightRow[FlysightPoint] =
    InputFlightRow(
      time = p.time.toInstant,
      altitude = p.hMSL,
      northSpeed = p.velN,
      eastSpeed = p.velE,
      verticalSpeed = p.velD,
      source = p,
    )

}
