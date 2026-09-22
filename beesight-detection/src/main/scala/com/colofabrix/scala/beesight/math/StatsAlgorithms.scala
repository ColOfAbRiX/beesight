package com.colofabrix.scala.beesight.math

import scala.math.*

/** Pure array-based statistical algorithms, decoupled from any container. */
private[math] object StatsAlgorithms {

  /** Population standard deviation of the given values. */
  def stdDev(values: Array[Double]): Double =
    val n     = values.length
    var sum   = 0.0
    var sumSq = 0.0

    var i = 0
    while i < n do
      val v = values(i)
      sum   += v
      sumSq += v * v
      i     += 1

    val mean = sum / n
    Math.sqrt(Math.max(0.0, sumSq / n - mean * mean))

  /**
   * Finds the inflection point where a signal transitions between a changing region and a stable one (or vice versa).
   *
   * Fits two-segment linear regressions for every candidate split point and returns the index that minimises total
   * squared error. Requires at least 4 elements; returns None otherwise.
   */
  def findInflection(values: Array[Double]): Option[Int] =
    val n = values.length
    if n < 4 then
      return None

    var bestIdx   = -1
    var bestError = Double.MaxValue

    var k = 2
    while k < n - 1 do
      val leftError  = segmentError(values, 0, k)
      val rightError = segmentError(values, k, n - k)
      val totalError = leftError + rightError

      if totalError < bestError then
        bestError = totalError
        bestIdx = k

      k += 1

    Option.when(bestIdx >= 0)(bestIdx)

  private def segmentError(data: Array[Double], start: Int, length: Int): Double =
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
