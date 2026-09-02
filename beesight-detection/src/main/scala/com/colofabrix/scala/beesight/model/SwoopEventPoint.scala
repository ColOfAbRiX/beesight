package com.colofabrix.scala.beesight.model

import java.time.Instant

/**
 * A point in the swoop phase representing a detected event.
 *
 * @param index    Zero-based index in the original data stream
 * @param altitude Altitude (m) at the event
 * @param time     Timestamp of the event
 * @param speed    Velocity vector at the event
 */
final private[beesight] case class SwoopEventPoint(
  index: Long,
  altitude: Double,
  time: Instant,
  speed: GeoVector,
)
