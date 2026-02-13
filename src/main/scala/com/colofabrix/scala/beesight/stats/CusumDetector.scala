package com.colofabrix.scala.beesight.stats

import breeze.linalg.DenseVector
import breeze.stats
import com.colofabrix.scala.beesight.collections.NonEmptyWindow.NonEmptyFilled
import com.colofabrix.scala.beesight.stats.CusumDetector.CusumState
import java.lang.Math.sqrt

/**
 * CUSUM (Cumulative Sum) algorithm for change detection
 *
 * This implementation follows the tabular CUSUM approach where we track positive and negative deviations separately.
 * The algorithm detects changes by accumulating deviations from a threshold value and signaling when the accumulated
 * sum exceeds a threshold.
 *
 * @param slack The slack parameter (K value) that reduces sensitivity to small deviations
 * @param threshold The threshold for detecting changes (H value in CUSUM literature)
 * @param f Extractor function to obtain the numeric value from type A
 *
 * See https://blog.stackademic.com/the-cusum-algorithm-all-the-essential-information-you-need-with-python-examples-f6a5651bf2e5
 */
final class CusumDetector[A] private (
  _state: CusumState,
  slack: Double,
  threshold: Double,
  f: A => Double,
) {

  private var state: CusumState =
    _state

  /**
   * Detects peaks in the signal based on the current window and value
   */
  def detectPeak(window: NonEmptyFilled[A], current: A): CusumDetector.Peak =
    val currentValue = f(current)
    val values       = window.toVector.map(f)
    val allValues: DenseVector[Double] = DenseVector((values :+ currentValue).toArray)

    val mean   = stats.mean(allValues)
    val stdDev = sqrt(stats.variance.population(allValues))

    val deviation   = currentValue - mean
    val positiveSum = Math.max(0.0, state.positiveSum + deviation - slack * stdDev)
    val negativeSum = Math.max(0.0, state.negativeSum - deviation - slack * stdDev)

    state = CusumState(positiveSum, negativeSum)

    val relativeThreshold = threshold * stdDev

    if positiveSum > relativeThreshold && positiveSum > negativeSum then
      CusumDetector.Peak.PositivePeak
    else if negativeSum > relativeThreshold && negativeSum > positiveSum then
      CusumDetector.Peak.NegativePeak
    else
      CusumDetector.Peak.Stable

}

object CusumDetector {

  enum Peak {
    case PositivePeak, Stable, NegativePeak
  }

  private case class CusumState(
    positiveSum: Double,
    negativeSum: Double,
  )

  def apply[A](slack: Double, threshold: Double)(f: A => Double): CusumDetector[A] =
    new CusumDetector(
      CusumState(0.0, 0.0),
      Math.max(slack, 0.0),
      Math.max(threshold, 0.0),
      f,
    )

}
