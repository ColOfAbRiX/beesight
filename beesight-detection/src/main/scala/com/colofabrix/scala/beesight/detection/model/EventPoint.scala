package com.colofabrix.scala.beesight.detection.model

import com.colofabrix.scala.beesight.detection.readers.PointReader
import java.time.Instant

/**
 * A point in the flight timeline
 *
 * @param index Zero-based index of this point in the original data stream
 * @param altitude Altitude (m) at which the event was detected
 * @param time Timestamp of the event, when known
 * @param speed Speed vector at the event, when known
 */
final case class EventPoint(
  index: Long,
  altitude: Double,
  time: Instant,
  speed: GeoVector,
)

object EventPoint {

  given PointReader[EventPoint] with {
    def time(point: EventPoint): Instant    = point.time
    def altitude(point: EventPoint): Double = point.altitude
    def speed(point: EventPoint): GeoVector = point.speed
  }

}
