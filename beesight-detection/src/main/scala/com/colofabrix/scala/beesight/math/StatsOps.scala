package com.colofabrix.scala.beesight.math

import cats.Foldable
import scala.reflect.ClassTag
import scala.math.*

extension [F[_]: Foldable, A](window: F[A]) {

  def medianBy[B: Median: ClassTag](f: A => B): B =
    Median[B].median(window.toArrayBy(f))

  def stdDevBy(f: A => Double): Double =
    StatsAlgorithms.stdDev(window.toArrayBy(f))

  def findInflectionBy(f: A => Double): Option[Int] =
    StatsAlgorithms.findInflection(window.toArrayBy(f))

}

extension [A: Fractional: ClassTag, F[_]: Foldable](window: F[A])
  def median: A =
    window.medianBy(identity)

extension (values: Array[Double])
  def median: Double =
    Median[Double].median(values)

extension (x: Array[Double])
  infix def slopeAgainst(y: Array[Double]): Double =
    StatsAlgorithms.slope(x, y)
