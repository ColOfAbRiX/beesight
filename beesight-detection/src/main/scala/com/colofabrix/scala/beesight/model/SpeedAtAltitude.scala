package com.colofabrix.scala.beesight.model

/**
 * A speed value with the altitude and the stream position at which it occurred
 *
 * FIXME: What kind of speed? Vertical? Horizontal? Total? which one?
 * FIXME: This is a question: is it better to reuse EventPoint rather than duplicating here?
 *
 * @param speed Speed (m/s)
 * @param altitude Altitude (m AGL) at which the speed was recorded
 * @param idx Index of the point in the stream where the value was found
 */
final case class SpeedAtAltitude(
  idx: Long,
  speed: Double,
  altitude: Double,
)
