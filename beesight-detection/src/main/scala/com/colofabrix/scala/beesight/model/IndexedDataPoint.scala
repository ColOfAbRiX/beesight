package com.colofabrix.scala.beesight.model

import java.time.Instant

/**
 * Internal representation of a flight data point with time, altitude, and velocity.
 */
final private[beesight] case class IndexedDataPoint(
  time: Instant,
  idx: Long,
  altitude: Double,
  speed: GeoVector,
)

object IndexedDataPoint {

  def stream[F[_], A: NormalizedPoint]: fs2.Pipe[F, A, IndexedDataPoint] =
    _.zipWithIndex
      .map {
        case (point, idx) => IndexedDataPoint(point.time, idx, point.altitude, point.speed)
      }

  given NormalizedPoint[IndexedDataPoint] with {
    def time(point: IndexedDataPoint): Instant    = point.time
    def altitude(point: IndexedDataPoint): Double = point.altitude
    def speed(point: IndexedDataPoint): GeoVector = point.speed
  }

}
