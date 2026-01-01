package com.colofabrix.scala.stats

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
    velD: Double,
    horizontalSpeed: Double,
    totalSpeed: Double,
    filteredVelD: Double,
  )

  /**
   * Computes flight metrics from raw GPS values
   */
  def computeMetrics(
    altitude: Double,
    velN: Double,
    velE: Double,
    velD: Double,
    velDWindow: Queue[Double],
  ): FlightMetrics =
    val horizontalSpeed = Math.sqrt(velN * velN + velE * velE)
    val totalSpeed = Math.sqrt(velN * velN + velE * velE + velD * velD)
    val filteredVelD = medianFilter(velDWindow, velD)

    FlightMetrics(
      altitude = altitude,
      velD = velD,
      horizontalSpeed = horizontalSpeed,
      totalSpeed = totalSpeed,
      filteredVelD = filteredVelD,
    )

  /**
   * Applies a median filter to reduce GPS jitter
   */
  def medianFilter(window: Queue[Double], currentValue: Double): Double =
    if window.isEmpty then
      currentValue
    else
      val values = (window.toSeq :+ currentValue).sorted
      val mid = values.length / 2
      if values.length % 2 == 0 then
        (values(mid - 1) + values(mid)) / 2.0
      else
        values(mid)

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
