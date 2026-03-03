package com.colofabrix.scala.beesight.detection.math

import breeze.linalg.DenseVector
import breeze.stats.median as breezeMedian
import cats.data.NonEmptyVector
import cats.syntax.all.*
import com.colofabrix.scala.beesight.collections.NEW
import com.colofabrix.scala.beesight.collections.NonEmptyWindow.NonEmptyFilled
import com.colofabrix.scala.beesight.model.GeoVector

object Stats {

  def median(xs: IterableOnce[Double]): Double =
    val iter = xs.iterator
    val arr  = iter.toArray
    if arr.isEmpty then 0.0 else breezeMedian(DenseVector(arr))

  def median(window: NEW[GeoVector]): GeoVector =
    val (east, north, vertical) =
      window.foldMap { vector =>
        (List(vector.east), List(vector.north), List(vector.vertical))
      }

    GeoVector(
      north = median(north),
      east = median(east),
      vertical = median(vertical),
    )

  def stdDev(window: NEW[Double]): Double =
    val vectorWindow = window.toVector
    val mean     = vectorWindow.sum / vectorWindow.size
    val variance = vectorWindow.map(v => Math.pow(v - mean, 2)).sum / vectorWindow.size
    Math.sqrt(variance)

  /**
   * Finds the inflection point where a signal transitions between a trend (increasing/decreasing) and a stable region,
   * or vice versa. Uses two-segment linear regression, returning the index that minimizes total squared error across
   * both segments.
   */
  def findInflection(window: NonEmptyFilled[Double]): Option[Int] =
    val data = window.toVector
    val n    = data.length

    if n < 4 then
      return None

    var bestIdx   = -1
    var bestError = Double.MaxValue

    var k = 2
    while k < n - 1 do
      val leftError  = segmentError(data, 0, k)
      val rightError = segmentError(data, k, n - k)
      val totalError = leftError + rightError

      if totalError < bestError then
        bestError = totalError
        bestIdx = k

      k += 1

    Option.when(bestIdx >= 0)(bestIdx)

  private def segmentError(data: Vector[Double], start: Int, length: Int): Double =
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
