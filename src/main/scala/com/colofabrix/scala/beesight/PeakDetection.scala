package com.colofabrix.scala.beesight

import breeze.linalg.*
import breeze.stats.*
import cats.effect.IO
import fs2.*
import java.lang.Math.*
import scala.collection.immutable.Queue

/**
 * Smoothed Z-Score algorithm for Peak Detection
 *
 * Adapted from https://stackoverflow.com/q/22583391
 *
 * @param lag The lag of the moving window that calculates the mean and standard deviation of historical data.
 * @param threshold The z-score at which the algorithm signals (how many standard deviations away from the moving mean a peak is)
 * @param influence The influence of new signals on the mean and standard deviation (how much a peak should affect other values near it)
 */
class PeakDetection(lag: Int, threshold: Double, influence: Double):
  import PeakDetection.*

  private val safeLag: Int =
    Math.max(lag, 1)

  private val safeThreshold: Double =
    Math.max(threshold, Double.MinPositiveValue)

  private val safeInfluence: Double =
    Math.min(Math.max(influence, 0.0), 1.0)

  def detect[A](f: A => Double): Pipe[IO, A, (A, Peak)] =
    detectStats(f).andThen(_.map(x => (x._1, x._2)))

  def detectStats[A](f: A => Double): Pipe[IO, A, (A, Peak, Stats)] =
    data =>
      data
        .scan(ScanState[A]()) {
          case (state @ ScanState(queue, _, _), a) if queue.length + 1 < safeLag =>
            state.copy(window = queue.enqueue(f(a)))

          case (ScanState(queue, _, _), a) =>
            val value = f(a)

            val window   = DenseVector[Double](queue.toArray)
            val pAvg     = mean(window)
            val pStdDev  = sqrt(variance.population(window))
            val isPeak   = abs(value - pAvg) > safeThreshold * pStdDev
            val filtered = safeInfluence * value + (1.0 - safeInfluence) * queue.last

            val (pushValue, peak) =
              if isPeak && value > pAvg then (filtered, Peak.PositivePeak)
              else if isPeak && value <= pAvg then (filtered, Peak.NegativePeak)
              else (value, Peak.Stable)

            ScanState(
              window = queue.dequeue._2.enqueue(pushValue),
              value = Some(a),
              stats = Stats(value, peak, pAvg, pStdDev),
            )
        }
        .collect {
          case ScanState(_, stats, Some(a)) => (a, stats.peak, stats)
        }

object PeakDetection:

  final case class Stats(
    value: Double = 0.0,
    peak: Peak = Peak.Stable,
    average: Double = 0.0,
    stdDev: Double = 0.0,
  )

  enum Peak:
    case PositivePeak
    case Stable
    case NegativePeak

  final private case class ScanState[A](
    window: Queue[Double] = Queue.empty,
    stats: Stats = Stats(),
    value: Option[A] = None,
  )
