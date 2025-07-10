package com.colofabrix.scala.stats

import breeze.linalg.*
import breeze.stats.*
import cats.effect.IO
import com.colofabrix.scala.stats.StdevStabilityDetector.*
import java.lang.Math.*
import scala.collection.immutable.Queue

/**
 * A detector that identifies stability or peaks in data based on standard deviation.
 *
 * This detector maintains a sliding window of values and calculates statistics (mean and
 * standard deviation) on this window. It determines if a new value represents a positive peak,
 * negative peak, or stable state based on how far it deviates from the window's mean.
 *
 * @param windowSize The size of the sliding window used for calculations
 * @param distance The number of standard deviations a value must deviate to be considered a peak
 */
final class StdevStabilityDetector(windowSize: Int, distance: Double):

  private val safeWindowSize: Int =
    Math.max(windowSize, 1)

  private val safeDistance: Double =
    Math.max(distance, 0.0)

  def checkNextValue(state: DetectorState, value: Double): DetectorState =
    state match {
      case DetectorState.Empty =>
        DetectorState.Filling(Queue(value))

      case DetectorState.Filling(window) if window.length < safeWindowSize =>
        DetectorState.Filling(window.enqueue(value))

      case DetectorState.Filling(window) =>
        calculateStats(value, window)

      case DetectorState.Detection(_, window, _, _, _) =>
        calculateStats(value, window)
    }

  private def calculateStats(value: Double, queue: Queue[Double]): DetectorState =
    val pAvg    = mean(queue)
    val pStdDev = sqrt(variance.population(queue))

    val peak =
      if value - pAvg > pStdDev * safeDistance then Peak.PositivePeak
      else if pAvg - value > pStdDev * safeDistance then Peak.NegativePeak
      else Peak.Stable

    val nextWindow =
      queue
        .dequeue
        ._2
        .enqueue(value)

    DetectorState.Detection(
      currentValue = value,
      window = nextWindow,
      windowAverage = pAvg,
      windowStDev = pStdDev,
      peakResult = peak,
    )

object StdevStabilityDetector {

  enum DetectorState {

    case Empty extends DetectorState

    case Filling(window: Queue[Double]) extends DetectorState

    case Detection(
      currentValue: Double,
      window: Queue[Double],
      windowAverage: Double,
      windowStDev: Double,
      peakResult: Peak,
    ) extends DetectorState

  }

}
