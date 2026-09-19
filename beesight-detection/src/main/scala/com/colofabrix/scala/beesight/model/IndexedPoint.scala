package com.colofabrix.scala.beesight.model

import java.time.Instant

/**
 * Internal representation of a flight data point with its position in the stream, time, altitude, and velocity.
 *
 * @param idx Zero-based position of this point in the data stream
 */
final private[beesight] case class IndexedPoint(
  idx: Long,
  time: Instant,
  altitude: Double,
  speed: GeoVector,
)

private[beesight] object IndexedPoint {

  def fromZip[A: PointReader](a: A, idx: Long): IndexedPoint =
    IndexedPoint(idx, a.time, a.altitude, a.speed)

  def stream[F[_], A: PointReader]: fs2.Pipe[F, A, IndexedPoint] =
    _.zipWithIndex.map(fromZip)

  given PointReader[IndexedPoint] with {
    def time(point: IndexedPoint): Instant    = point.time
    def altitude(point: IndexedPoint): Double = point.altitude
    def speed(point: IndexedPoint): GeoVector = point.speed
  }

}
