package com.colofabrix.scala.beesight.model

/**
 * A point in the flight timeline
 *
 * @param index Zero-based index of this point in the original data stream
 * @param altitude Altitude (m) at which the event was detected
 */
// FIXME: Consider adding a `time` and `speed` fields
final case class EventPoint(
  index: Long,
  altitude: Double,
)
