package com.colofabrix.scala.beesight.model

import java.time.Instant

/**
 * Internal representation of a flight data point with time, altitude, and velocity.
 */
final private[beesight] case class IndexedPoint(idx: Long, point: DataPoint)

private[beesight] object IndexedPoint {

  def stream[F[_], A: PointReader]: fs2.Pipe[F, A, IndexedPoint] =
    _.zipWithIndex
      .map {
        case (point, idx) => IndexedPoint(idx, DataPoint.fromReader(point))
      }

  given PointReader[IndexedPoint] with {
    def time(point: IndexedPoint): Instant    = point.point.time
    def altitude(point: IndexedPoint): Double = point.point.altitude
    def speed(point: IndexedPoint): GeoVector = point.point.speed
  }

}
