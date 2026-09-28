package com.colofabrix.scala.beesight.math

import cats.Foldable
import scala.math.*
import scala.reflect.ClassTag

extension [F[_]: Foldable, A](window: F[A]) {

  /**
   * Median of the elements projected by `f`, using the given [[Median]] instance.
   */
  private[beesight] def medianBy[B: Median: ClassTag](f: A => B): B =
    Median[B].median(window.toArrayBy(f))

  /**
   * Population standard deviation of the elements projected by `f`.
   */
  private[beesight] def stdDevBy(f: A => Double): Double =
    StatsAlgorithms.stdDev(window.toArrayBy(f))

  /**
   * Index at which the elements projected by `f` best split into two linear segments, each fitted by
   * least squares and scored by total squared error; see [[StatsAlgorithms.findInflection]].
   */
  private[beesight] def findInflectionBy(f: A => Double): Option[Int] =
    StatsAlgorithms.findInflection(window.toArrayBy(f))

}

extension [A: Fractional: ClassTag, F[_]: Foldable](window: F[A])
  /**
   * Median of the window's elements, using the low-priority `Fractional`-based [[Median]] instance.
   */
  private[beesight] def median: A =
    window.medianBy(identity)

extension (values: Array[Double])
  /**
   * Median of the array's values, using the specialised `Median[Double]` instance (insertion sort up to 8
   * elements, otherwise a full sort).
   */
  private[beesight] def median: Double =
    Median[Double].median(values)

extension (x: Array[Double])
  /**
   * Least-squares slope of `y` against `x` (the dependent variable against the receiver).
   */
  private[beesight] infix def slopeAgainst(y: Array[Double]): Double =
    StatsAlgorithms.slope(x, y)
