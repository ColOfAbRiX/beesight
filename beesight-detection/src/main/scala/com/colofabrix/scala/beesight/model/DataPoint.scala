package com.colofabrix.scala.beesight.model

import java.time.Instant

private[beesight] trait NormalizedPoint[A] {
  def time(point: A): Instant
  def altitude(point: A): Double
  def speed(point: A): GeoVector
}

object NormalizedPoint {

  def apply[A: NormalizedPoint]: NormalizedPoint[A] = summon

  def extractAs[A: NormalizedPoint, B](a: A): NormalizedPoint[B] =
    new NormalizedPoint[B] { 
      def time(point: B): Instant = a.time
      def altitude(point: B): Double = a.altitude
      def speed(point: B): GeoVector = a.speed
    }

}

extension [A: NormalizedPoint](self: A) {
  def time: Instant    = NormalizedPoint[A].time(self)
  def altitude: Double = NormalizedPoint[A].altitude(self)
  def speed: GeoVector = NormalizedPoint[A].speed(self)
}
