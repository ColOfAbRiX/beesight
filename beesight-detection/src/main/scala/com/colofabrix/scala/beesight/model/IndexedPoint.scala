package com.colofabrix.scala.beesight.model

import com.colofabrix.scala.beesight.math.*
import com.colofabrix.scala.beesight.readers.*
import java.time.Instant

/**
 * Internal representation of a flight data point with its position in the stream, time, altitude, and velocity.
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

  extension (self: IndexedPoint)
    def toEventPoint: EventPoint =
      EventPoint(
        index = self.idx,
        altitude = self.altitude,
        time = self.time,
        speed = self.speed,
      )

  given PointReader[IndexedPoint] with {
    def time(point: IndexedPoint): Instant    = point.time
    def altitude(point: IndexedPoint): Double = point.altitude
    def speed(point: IndexedPoint): GeoVector = point.speed
  }

  given Interpolation[IndexedPoint] with
    def interpolate(p1: IndexedPoint, p2: IndexedPoint, time: Instant): IndexedPoint =
      IndexedPoint(
        idx = p2.idx,
        time = time,
        altitude = Lerp[Double].lerp(p1.altitude, p2.altitude, p1.time, p2.time, time),
        speed = Lerp[GeoVector].lerp(p1.speed, p2.speed, p1.time, p2.time, time),
      )

}
