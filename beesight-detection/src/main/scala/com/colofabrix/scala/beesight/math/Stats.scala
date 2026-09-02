package com.colofabrix.scala.beesight.math

import cats.Foldable
import cats.syntax.all.*
import com.colofabrix.scala.beesight.collections.NEW
import com.colofabrix.scala.beesight.collections.NonEmptyWindow.NonEmptyFilled
import com.colofabrix.scala.beesight.model.GeoVector

private[beesight] object Stats {

  /**
   * Component-wise median of GeoVectors extracted from a window.
   */
  def medianBy[A](window: NEW[A])(f: A => GeoVector): GeoVector =
    val n        = window.length
    val eastArr  = new Array[Double](n)
    val northArr = new Array[Double](n)
    val vertArr  = new Array[Double](n)
    var i        = 0

    window.foldLeft(()) { (_, a) =>
      val v = f(a)
      eastArr(i) = v.east
      northArr(i) = v.north
      vertArr(i) = v.vertical
      i += 1
    }

    GeoVector(
      north = median(northArr),
      east = median(eastArr),
      vertical = median(vertArr),
    )

  /**
   * Component-wise median of GeoVectors from the `n` newest elements of a window plus one extra GeoVector.
   * Zero-allocation alternative to `takeNewest` + `enqueue` + `medianBy`.
   */
  def medianGeoVectorWithExtra[A](window: NEW[A], n: Int, extra: GeoVector)(f: A => GeoVector): GeoVector =
    val count    = Math.max(1, Math.min(n, window.length))
    val total    = count + 1
    val eastArr  = new Array[Double](total)
    val northArr = new Array[Double](total)
    val vertArr  = new Array[Double](total)
    var i        = 0

    window.foldNewest(n)(()) { (_, a) =>
      val v = f(a)
      eastArr(i) = v.east
      northArr(i) = v.north
      vertArr(i) = v.vertical
      i += 1
    }

    eastArr(i) = extra.east
    northArr(i) = extra.north
    vertArr(i) = extra.vertical

    GeoVector(
      north = median(northArr),
      east = median(eastArr),
      vertical = median(vertArr),
    )

  /**
   * Population standard deviation of values extracted from a window.
   */
  def stdDevBy[A](window: NEW[A])(f: A => Double): Double =
    val n     = window.length
    var sum   = 0.0
    var sumSq = 0.0

    window.foldLeft(()) { (_, a) =>
      val v = f(a)
      sum   += v
      sumSq += v * v
    }

    val mean = sum / n
    Math.sqrt(Math.max(0.0, sumSq / n - mean * mean))

  /**
   * Finds the inflection point where a signal transitions between a changing region and a stable one (or vice versa).
   *
   * Fits two-segment linear regressions for every candidate split point and returns the index that minimises total
   * squared error. Requires at least 4 elements; returns None otherwise.
   */
  def findInflectionBy[F[_]: Foldable, A](window: F[A])(f: A => Double): Option[Int] =
    val n = window.size.toInt
    if n < 4 then
      return None

    val data = new Array[Double](n)

    var idx = 0
    window.foldLeft(()) { (_, a) =>
      data(idx) = f(a)
      idx += 1
    }

    findInflectionFromArray(data, n)

  /**
   * Computes the median of an array of doubles.
   */
  private def median(values: Array[Double]): Double =
    val n = values.length
    if n <= 8 then
      insertionSort(values)
      if n % 2 == 0 then (values(n / 2 - 1) + values(n / 2)) / 2.0
      else values(n / 2)
    else
      val sorted = values.sorted
      if n % 2 == 0 then (sorted(n / 2 - 1) + sorted(n / 2)) / 2.0
      else sorted(n / 2)

  /** In-place insertion sort for small arrays. */
  private def insertionSort(a: Array[Double]): Unit =
    var i = 1
    while i < a.length do
      val key = a(i)
      var j   = i - 1
      while j >= 0 && a(j) > key do
        a(j + 1) = a(j)
        j -= 1
      a(j + 1) = key
      i += 1

  private def findInflectionFromArray(data: Array[Double], n: Int): Option[Int] =
    var bestIdx   = -1
    var bestError = Double.MaxValue

    var k = 2
    while k < n - 1 do
      val leftError  = segmentErrorArr(data, 0, k)
      val rightError = segmentErrorArr(data, k, n - k)
      val totalError = leftError + rightError

      if totalError < bestError then
        bestError = totalError
        bestIdx = k

      k += 1

    Option.when(bestIdx >= 0)(bestIdx)

  private def segmentErrorArr(data: Array[Double], start: Int, length: Int): Double =
    if length <= 1 then
      return 0.0

    val n     = length.toDouble
    var sumX  = 0.0
    var sumY  = 0.0
    var sumXX = 0.0
    var sumXY = 0.0
    var sumYY = 0.0

    var i = 0
    while i < length do
      val x = i.toDouble
      val y = data(start + i)
      sumX  += x
      sumY  += y
      sumXX += x * x
      sumXY += x * y
      sumYY += y * y
      i     += 1

    val denom = n * sumXX - sumX * sumX

    if Math.abs(denom) < 1e-12 then
      val mean = sumY / n
      Math.max(0.0, sumYY - n * mean * mean)
    else
      val slope     = (n * sumXY - sumX * sumY) / denom
      val intercept = (sumY - slope * sumX) / n
      Math.max(0.0, sumYY - intercept * sumY - slope * sumXY)

}
