package com.colofabrix.scala.beesight.stats

import breeze.linalg.DenseVector
import breeze.stats.median
import scala.collection.immutable.Queue

/**
 * Processes raw GPS signals to compute derived metrics and apply filtering
 */
object SignalProcessor {

  /**
   * Computed flight metrics from raw GPS data
   */
  final case class FlightMetrics(
    altitude: Double,
    verticalSpeed: Double,
    horizontalSpeed: Double,
    totalSpeed: Double,
    filteredVerticalSpeed: Double,
  )

  /**
   * Computes flight metrics from raw GPS values
   */
  def computeMetrics(
    altitude: Double,
    northSpeed: Double,
    eastSpeed: Double,
    verticalSpeed: Double,
    verticalSpeedWindow: Queue[Double],
  ): FlightMetrics =
    val horizontalSpeed       = Math.sqrt(northSpeed * northSpeed + eastSpeed * eastSpeed)
    val totalSpeed            = Math.sqrt(northSpeed * northSpeed + eastSpeed * eastSpeed + verticalSpeed * verticalSpeed)
    val filteredVerticalSpeed = medianFilter(verticalSpeedWindow, verticalSpeed)

    FlightMetrics(
      altitude = altitude,
      verticalSpeed = verticalSpeed,
      horizontalSpeed = horizontalSpeed,
      totalSpeed = totalSpeed,
      filteredVerticalSpeed = filteredVerticalSpeed,
    )

  /**
   * Applies a median filter to reduce GPS jitter
   */
  def medianFilter(window: Queue[Double], currentValue: Double): Double =
    if window.isEmpty then
      currentValue
    else
      median(DenseVector((window.toSeq :+ currentValue).toArray))

  /**
   * Maintains a sliding window of values for filtering
   */
  def updateWindow(window: Queue[Double], value: Double, maxSize: Int): Queue[Double] =
    val updated = window.enqueue(value)
    if updated.size > maxSize then
      updated.dequeue._2
    else
      updated

}
