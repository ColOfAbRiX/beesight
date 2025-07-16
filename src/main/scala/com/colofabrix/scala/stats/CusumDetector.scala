package com.colofabrix.scala.stats

import breeze.linalg.*
import breeze.stats.*
import cats.effect.IO
import com.colofabrix.scala.stats.CusumDetector.*
import java.lang.Math.*
import scala.collection.immutable.Queue

/**
 * CUSUM (Cumulative Sum) algorithm for change detection
 *
 * This implementation follows the tabular CUSUM approach where we track positive and negative deviations separately.
 * The algorithm detects changes by accumulating deviations from a threshold value and signaling when the accumulated
 * sum exceeds a threshold.
 *
 * @param windowSize The size of the sliding window used for calculating the mean and standard deviation
 * @param threshold The threshold for detecting changes (K value in CUSUM literature)
 *
 * See https://blog.stackademic.com/the-cusum-algorithm-all-the-essential-information-you-need-with-python-examples-f6a5651bf2e5
 */
final class CusumDetector private (windowSize: Int, slack: Double, threshold: Double):

  def checkNextValue(state: DetectorState, value: Double): DetectorState =
    state match {
      case DetectorState.Empty =>
        DetectorState.Filling(Queue(value))

      case DetectorState.Filling(window) if window.length < windowSize =>
        DetectorState.Filling(window.enqueue(value))

      case DetectorState.Filling(window) =>
        calculateStats(value, window, 0.0, 0.0)

      case DetectorState.Detection(_, window, _, _, _, positiveSum, negativeSum) =>
        calculateStats(value, window, positiveSum, negativeSum)
    }

  private def calculateStats(
    value: Double,
    queue: Queue[Double],
    prevPositiveSum: Double,
    prevNegativeSum: Double,
  ): DetectorState =
    val pAvg      = mean(queue)
    val pStdDev   = sqrt(variance.population(queue))
    val deviation = value - pAvg

    // Calculate the positive and negative CUSUM values
    // S+ = max(0, S+ + (x - μ) - K*σ)
    // S- = max(0, S- - (x - μ) - K*σ)
    val positiveSum = Math.max(0.0, prevPositiveSum + deviation - slack * pStdDev)
    val negativeSum = Math.max(0.0, prevNegativeSum - deviation - slack * pStdDev)

    // The threshold is scaled by the standard deviation
    val relativeThreshold = threshold * pStdDev

    // Determine if there's a peak based on the CUSUM values
    val peak =
      if positiveSum > relativeThreshold && positiveSum > negativeSum then Peak.PositivePeak
      else if negativeSum > relativeThreshold && negativeSum > positiveSum then Peak.NegativePeak
      else Peak.Stable

    // Update the sliding window
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
      positiveSum = positiveSum,
      negativeSum = negativeSum,
    )

object CusumDetector {

  def apply(windowSize: Int, slack: Double, threshold: Double): CusumDetector =
    new CusumDetector(
      windowSize = Math.max(windowSize, 1),
      threshold = Math.max(threshold, 0.0),
      slack = Math.max(slack, 0.0)
    )

  enum DetectorState {

    case Empty extends DetectorState

    case Filling(window: Queue[Double]) extends DetectorState

    case Detection(
      currentValue: Double,
      window: Queue[Double],
      windowAverage: Double,
      windowStDev: Double,
      peakResult: Peak,
      positiveSum: Double = 0.0,
      negativeSum: Double = 0.0,
    ) extends DetectorState

  }

}
