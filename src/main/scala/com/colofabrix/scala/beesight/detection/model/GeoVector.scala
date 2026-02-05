package com.colofabrix.scala.beesight.detection.model

import java.lang.Math.*
import cats.Order

/**
 * Represents a 3D velocity vector with north, east, and vertical components.
 */
final case class GeoVector(
  north: Double,
  east: Double,
  vertical: Double,
)

object GeoVector {

  extension (self: GeoVector) {

    infix def -(other: GeoVector): GeoVector =
      GeoVector(
        north = self.north - other.north,
        east = self.east - other.east,
        vertical = self.vertical - other.vertical,
      )

    infix def +(other: GeoVector): GeoVector =
      GeoVector(
        north = self.north + other.north,
        east = self.east + other.east,
        vertical = self.vertical + other.vertical,
      )

    infix def *(scalar: Double): GeoVector =
      GeoVector(
        north = self.north * scalar,
        east = self.east * scalar,
        vertical = self.vertical * scalar,
      )

    infix def /(scalar: Double): GeoVector =
      GeoVector(
        north = self.north / scalar,
        east = self.east / scalar,
        vertical = self.vertical / scalar,
      )

    infix def <(scalar: Double): Boolean =
      magnitude < scalar

    infix def <=(scalar: Double): Boolean =
      magnitude <= scalar

    infix def >=(scalar: Double): Boolean =
      magnitude >= scalar

    infix def >(scalar: Double): Boolean =
      magnitude > scalar

    def horizontal: Double =
      sqrt(pow(self.east, 2) + pow(self.north, 2))

    def magnitude: Double =
      sqrt(pow(self.east, 2) + pow(self.north, 2) + pow(self.vertical, 2))

  }

  // given Order[GeoVector] with {
  //   def compare(x: GeoVector, y: GeoVector): Int =
  //     java.lang.Double.compare(x.magnitude, y.magnitude)
  // }

  // given Ordering[GeoVector] =
  //   implicitly[Order[GeoVector]].toOrdering

}
