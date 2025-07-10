package com.colofabrix.scala.stats

import breeze.linalg.*
import breeze.stats.*
import cats.effect.IO
import fs2.*
import java.lang.Math.*
import scala.collection.immutable.Queue

/**
 * A detector that identifies peaks in data using the Smoothed Z-Score algorithm.
 *
 * This detector maintains a sliding window of values and calculates the mean and standard deviation of this window. It
 * determines if a new value is a peak based on its Z-Score, which measures how many standard deviations the value is
 * away from the mean.
 *
 * When a peak is detected, its influence on future calculations can be controlled to prevent a single large peak from
 * affecting subsequent peak detection. This is achieved by filtering the value that gets added to the window.
 *
 * Adapted from the algorithm described at: https://stackoverflow.com/q/22583391
 *
 * @param windowSize The size of the sliding window used for calculating mean and standard deviation
 * @param threshold The Z-Score threshold for peak detection (how many standard deviations from the mean)
 * @param influence The influence of peaks on future calculations (between 0.0 and 1.0)
 */
final class ZScorePeakDetector(windowSize: Int, threshold: Double, influence: Double):
  import ZScorePeakDetector.*

  private val safeWindowSize: Int =
    Math.max(windowSize, 1)

  private val safeThreshold: Double =
    Math.max(threshold, Double.MinPositiveValue)

  private val safeInfluence: Double =
    Math.min(Math.max(influence, 0.0), 1.0)

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
    val pAvg     = mean(queue)
    val pStdDev  = sqrt(variance.population(queue))
    val isPeak   = abs(value - pAvg) > safeThreshold * pStdDev
    val filtered = safeInfluence * value + (1.0 - safeInfluence) * queue.last

    val (pushValue, peak) =
      if isPeak && value > pAvg then (filtered, Peak.PositivePeak)
      else if isPeak && value <= pAvg then (filtered, Peak.NegativePeak)
      else (value, Peak.Stable)

    val nextWindow =
      queue
        .dequeue
        ._2
        .enqueue(pushValue)

    DetectorState.Detection(
      currentValue = value,
      window = nextWindow,
      windowAverage = pAvg,
      windowStDev = pStdDev,
      peakResult = peak,
    )

object ZScorePeakDetector {

  /**
   * Status of the rolling peak detection
   */
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
