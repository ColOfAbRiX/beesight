package com.colofabrix.scala.beesight.detection.stats

import breeze.linalg.DenseVector
import breeze.stats
import com.colofabrix.scala.beesight.collections.NonEmptyWindow.NonEmptyFilled
import java.lang.Math.sqrt

/**
 * A detector that identifies stability or peaks in data based on standard deviation.
 *
 * This detector calculates statistics (mean and standard deviation) on a filled window
 * and determines if a new value represents a stable or unstable state based on how far
 * it deviates from the window's mean.
 *
 * @param distance The number of standard deviations a value must deviate to be considered unstable
 * @param f Extractor function to obtain the numeric value from type A
 */
final class StdevStabilityDetector[A] private (
  distance: Double,
  f: A => Double,
) {

  /**
   * Detects stability based on the current window and value
   */
  def detectStability(window: NonEmptyFilled[A], current: A): StdevStabilityDetector.Stability =
    val currentValue = f(current)
    val values       = window.toVector.map(f)
    val allValues: DenseVector[Double] = DenseVector((values :+ currentValue).toArray)

    val mean   = stats.mean(allValues)
    val stdDev = sqrt(stats.variance.population(allValues))

    val deviation = Math.abs(currentValue - mean)

    if deviation > stdDev * distance then
      StdevStabilityDetector.Stability.Unstable
    else
      StdevStabilityDetector.Stability.Stable

}

object StdevStabilityDetector {

  enum Stability {
    case Stable, Unstable
  }

  def apply[A](distance: Double)(f: A => Double): StdevStabilityDetector[A] =
    new StdevStabilityDetector(
      Math.max(distance, 0.0),
      f,
    )

}
