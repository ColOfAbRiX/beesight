package com.colofabrix.scala.beesight

import breeze.linalg.*
import breeze.stats.*
import cats.effect.IO
import fs2.*
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

  def detect: Pipe[Pure, Double, (Double, Peak)] =
    data =>
      data
        .scan(ScanState()) {
          case (state @ ScanState(queue, false, _, _), value) =>
            state.copy(
              window = queue.enqueue(value),
              ready = queue.length + 1 >= safeLag,
            )

          case (ScanState(queue, true, _, _), value) =>
            val window  = DenseVector[Double](queue.toArray)
            val pAvg    = mean(window)
            val pStdDev = Math.sqrt(variance.population(window))

            if Math.abs(value - pAvg) > safeThreshold * pStdDev then
              val filtered = safeInfluence * value + (1.0 - safeInfluence) * queue.last
              if value > pAvg then
                ScanState(queue.push(filtered), true, Peak.PositivePeak, Some(value))
              else
                ScanState(queue.push(filtered), true, Peak.NegativePeak, Some(value))
            else
              ScanState(queue.push(value), true, Peak.Stable, Some(value))
        }
        .collect { case ScanState(_, true, peak, Some(value)) => (value, peak) }

object PeakDetection:

  enum Peak:
    case PositivePeak
    case Stable
    case NegativePeak

  final private case class ScanState(
    window: Queue[Double] = Queue.empty,
    ready: Boolean = false,
    peak: Peak = Peak.Stable,
    value: Option[Double] = None,
  )

  extension [A](self: Queue[A])
    def push(a: A): Queue[A] =
      self.dequeue._2.enqueue(a)
