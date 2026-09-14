package com.colofabrix.scala.beesight.model

/**
 * A speed value with the altitude at which it occurred
 *
 * @param speed Speed (m/s)
 * @param altitude Altitude (m AGL) at which the speed was recorded
 */
final private[beesight] case class SpeedAtAltitude(
  speed: Double,
  altitude: Double,
)
