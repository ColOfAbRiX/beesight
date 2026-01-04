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
    velocityDown: Double,
    horizontalSpeed: Double,
    totalSpeed: Double,
    filteredVelocityDown: Double,
  )

  /**
   * Computes flight metrics from raw GPS values
   */
  def computeMetrics(
    altitude: Double,
    velocityNorth: Double,
    velocityEast: Double,
    velocityDown: Double,
    velocityDownWindow: Queue[Double],
  ): FlightMetrics =
    val horizontalSpeed      = Math.sqrt(velocityNorth * velocityNorth + velocityEast * velocityEast)
    val totalSpeed           = Math.sqrt(velocityNorth * velocityNorth + velocityEast * velocityEast + velocityDown * velocityDown)
    val filteredVelocityDown = medianFilter(velocityDownWindow, velocityDown)

    FlightMetrics(
      altitude = altitude,
      velocityDown = velocityDown,
      horizontalSpeed = horizontalSpeed,
      totalSpeed = totalSpeed,
      filteredVelocityDown = filteredVelocityDown,
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
