package com.colofabrix.scala.beesight.model

/**
 * A speed value with the altitude at which it occurred
 * 
 * FIXME: Add idx: Long to SpeedAtAltitude to indicate the point in the stream where the value was found 
 *
 * @param speed Speed (m/s)
 * @param altitude Altitude (m AGL) at which the speed was recorded
 */
final case class SpeedAtAltitude(
  speed: Double,
  altitude: Double,
)
