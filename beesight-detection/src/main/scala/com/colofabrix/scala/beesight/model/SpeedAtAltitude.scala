package com.colofabrix.scala.beesight.model

/**
 * A speed magnitude at a given altitude and stream position. The speed is a scalar (m/s) whose physical
 * meaning (vertical, horizontal, or total) is determined by the context in which this value is stored
 * (e.g. [[com.colofabrix.scala.beesight.model.SwoopData.maxVerticalSpeed]],
 * [[com.colofabrix.scala.beesight.model.SwoopData.maxTotalSpeed]],
 * [[com.colofabrix.scala.beesight.model.SwoopData.maxHorizontalSpeed]]).
 *
 * @param speed Speed magnitude (m/s)
 * @param altitude Altitude (m AGL) at which the speed was recorded
 * @param idx Index of the point in the stream where the value was found
 */
final case class SpeedAtAltitude(
  idx: Long,
  speed: Double,
  altitude: Double,
)
