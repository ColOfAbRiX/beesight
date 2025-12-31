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
final class CusumDetector private (
  windowSize: Int,
  slack: Double,
  threshold: Double,
  meanFn: WinStat,
  stdDevFn: WinStat,
):

  def checkNextValue(state: DetectorState, value: Double): DetectorState =
    state match {
      case DetectorState.Empty =>
        DetectorState.Filling(Queue(value))

      case DetectorState.Filling(window) if window.length < windowSize =>
        DetectorState.Filling(window.enqueue(value))

      case DetectorState.Filling(window) =>
        calculateStats(value, window, 0.0, 0.0)

      case DetectorState.Detection(_, window, _, _, positiveSum, negativeSum, _) =>
        calculateStats(value, window, positiveSum, negativeSum)
    }

  private def calculateStats(
    value: Double,
    queue: Queue[Double],
    prevPositiveSum: Double,
    prevNegativeSum: Double,
  ): DetectorState =
    val pStat     = meanFn(queue, value)
    val pStdDev   = stdDevFn(queue, value)
    val deviation = value - pStat

    val positiveSum = Math.max(0.0, prevPositiveSum + deviation - slack * pStdDev)
    val negativeSum = Math.max(0.0, prevNegativeSum - deviation - slack * pStdDev)

    val relativeThreshold = threshold * pStdDev

    val peak =
      if positiveSum > relativeThreshold && positiveSum > negativeSum then Peak.PositivePeak
      else if negativeSum > relativeThreshold && negativeSum > positiveSum then Peak.NegativePeak
      else Peak.Stable

    val nextWindow =
      queue
        .dequeue
        ._2
        .enqueue(value)

    DetectorState.Detection(
      currentValue = value,
      window = nextWindow,
      windowAverage = pStat,
      windowStDev = pStdDev,
      positiveSum = positiveSum,
      negativeSum = negativeSum,
      peakResult = peak,
    )

object CusumDetector {

  private type WinStat = (Queue[Double], Double) => Double

  private val MeanFn: WinStat =
    (window, current) => breeze.stats.mean(DenseVector(window.enqueue(current).toArray))

  private val MedianFn: WinStat =
    (window, current) => breeze.stats.median(DenseVector(window.enqueue(current).toArray))

  private val StdDevFn: WinStat =
    (window, current) => sqrt(breeze.stats.variance.population(DenseVector(window.enqueue(current).toArray)))

  private def EMA(tailSize: Int): WinStat =
    (window, current) =>
      if window.isEmpty then
        current
      else
        val alpha = 1.0 / tailSize.toDouble
        alpha * window.dequeue._1 + (1.0 - alpha) * current

  // This is incorrect
  private def EMAStdDev(tailSize: Int): WinStat =
    (window, current) =>
      if window.isEmpty then
        current
      else
        val alpha = 1.0 / tailSize.toDouble
        alpha * window(0) + (1.0 - alpha) * window(1)

  def apply(windowSize: Int, slack: Double, threshold: Double, meanFn: WinStat, stdDevFn: WinStat): CusumDetector =
    new CusumDetector(
      windowSize = Math.max(windowSize, 1),
      threshold = Math.max(threshold, 0.0),
      slack = Math.max(slack, 0.0),
      meanFn = meanFn,
      stdDevFn = stdDevFn,
    )

  def withMean(windowSize: Int, slack: Double, threshold: Double): CusumDetector =
    new CusumDetector(
      windowSize = Math.max(windowSize, 1),
      threshold = Math.max(threshold, 0.0),
      slack = Math.max(slack, 0.0),
      meanFn = MeanFn,
      stdDevFn = StdDevFn,
    )

  def withMedian(windowSize: Int, slack: Double, threshold: Double): CusumDetector =
    new CusumDetector(
      windowSize = Math.max(windowSize, 1),
      threshold = Math.max(threshold, 0.0),
      slack = Math.max(slack, 0.0),
      meanFn = MedianFn,
      stdDevFn = StdDevFn,
    )

  def withEMA(windowSize: Int, slack: Double, threshold: Double): CusumDetector =
    new CusumDetector(
      windowSize = 1,
      threshold = Math.max(threshold, 0.0),
      slack = Math.max(slack, 0.0),
      meanFn = EMA(windowSize),
      stdDevFn = StdDevFn,
    )

  enum DetectorState {

    case Empty extends DetectorState

    case Filling(window: Queue[Double]) extends DetectorState

    case Detection(
      currentValue: Double,
      window: Queue[Double],
      windowAverage: Double,
      windowStDev: Double,
      positiveSum: Double = 0.0,
      negativeSum: Double = 0.0,
      peakResult: Peak,
    ) extends DetectorState

  }

}
