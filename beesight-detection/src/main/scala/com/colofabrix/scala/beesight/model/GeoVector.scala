package com.colofabrix.scala.beesight.model

import cats.Order
import com.colofabrix.scala.beesight.math.*
import java.lang.Math.*
import java.time.Instant

/**
 * Represents a 3D velocity vector with north, east, and vertical components.
 *
 * @param north Northward component (m/s)
 * @param east Eastward component (m/s)
 * @param vertical Vertical component (m/s; positive = downward for Flysight convention)
 */
final private[beesight] case class GeoVector(
  north: Double,
  east: Double,
  vertical: Double,
)

object GeoVector {

  /**
   * The zero vector (0, 0, 0).
   */
  val zero: GeoVector =
    GeoVector(0.0, 0.0, 0.0)

  extension (self: GeoVector) {

    /** Component-wise subtraction. */
    infix def -(other: GeoVector): GeoVector =
      GeoVector(
        north = self.north - other.north,
        east = self.east - other.east,
        vertical = self.vertical - other.vertical,
      )

    /** Component-wise addition. */
    infix def +(other: GeoVector): GeoVector =
      GeoVector(
        north = self.north + other.north,
        east = self.east + other.east,
        vertical = self.vertical + other.vertical,
      )

    /** Scalar multiplication of all components. */
    infix def *(scalar: Double): GeoVector =
      GeoVector(
        north = self.north * scalar,
        east = self.east * scalar,
        vertical = self.vertical * scalar,
      )

    /** Scalar (dot) product of the two vectors. */
    infix def dot(other: GeoVector): Double =
      self.north * other.north + self.east * other.east + self.vertical * other.vertical

    /** Scalar division of all components. */
    infix def /(scalar: Double): GeoVector =
      GeoVector(
        north = self.north / scalar,
        east = self.east / scalar,
        vertical = self.vertical / scalar,
      )

    /** True if magnitude is strictly less than the given scalar. */
    infix def <(scalar: Double): Boolean =
      magnitude < scalar

    /** True if magnitude is less than or equal to the given scalar. */
    infix def <=(scalar: Double): Boolean =
      magnitude <= scalar

    /** True if magnitude is greater than or equal to the given scalar. */
    infix def >=(scalar: Double): Boolean =
      magnitude >= scalar

    /** True if magnitude is strictly greater than the given scalar. */
    infix def >(scalar: Double): Boolean =
      magnitude > scalar

    /** Horizontal speed: magnitude of the north and east components. */
    def horizontal: Double =
      sqrt(self.east * self.east + self.north * self.north)

    /** 3D magnitude (Euclidean norm) of the vector. */
    def magnitude: Double =
      sqrt(self.east * self.east + self.north * self.north + self.vertical * self.vertical)
      
  }

  /**
   * Cats Order instance comparing GeoVectors by magnitude.
   */
  given Order[GeoVector] with {
    def compare(x: GeoVector, y: GeoVector): Int =
      java.lang.Double.compare(x.magnitude, y.magnitude)
  }

  /**
   * Scala Ordering instance derived from the Cats Order.
   */
  given Ordering[GeoVector] =
    implicitly[Order[GeoVector]].toOrdering

  given Lerp[GeoVector] with {
    def lerp(a: GeoVector, b: GeoVector, t1: Instant, t2: Instant, t: Instant): GeoVector =
      a + (b - a) * Lerp.tParam(t1, t2, t)
  }

  given Median[GeoVector] with {
    def median(values: Array[GeoVector]): GeoVector =
      val n     = values.length
      val north = new Array[Double](n)
      val east  = new Array[Double](n)
      val vert  = new Array[Double](n)

      var i = 0
      while i < n do
        val v = values(i)
        north(i) = v.north
        east(i) = v.east
        vert(i) = v.vertical
        i += 1

      GeoVector(
        north = north.median,
        east = east.median,
        vertical = vert.median,
      )
  }

}
