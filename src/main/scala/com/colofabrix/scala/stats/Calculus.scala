package com.colofabrix.scala.stats

import breeze.linalg.*
import breeze.stats.*
import scala.collection.immutable.Queue
import java.time.Instant

/**
 * Utility object for calculus operations on time-series data
 */
object Calculus {

  /**
   * Represents a value with its associated timestamp
   */
  final case class TimedValue(value: Double, time: Instant)

  /**
   * Calculates the derivative between two windows of values by averaging each window
   */
  def windowDerivative(current: Queue[TimedValue], previous: Queue[TimedValue]): Double =
    val avgCurrentValue = mean(current.map(_.value))
    val avgPreviousValue = mean(previous.map(_.value))

    val currentTime = current.last.time
    val previousTime = previous.last.time

    val avgCurrent = TimedValue(avgCurrentValue, currentTime)
    val avgPrevious = TimedValue(avgPreviousValue, previousTime)

    derivative(avgPrevious, avgCurrent)

  /**
   * Calculates the rate of change between two timed values (change in value divided by change in time)
   */
  def derivative(olderValue: TimedValue, newerValue: TimedValue): Double =
    val valueDelta = newerValue.value - olderValue.value
    val timeDelta  = java.time.Duration.between(olderValue.time, newerValue.time).toMillis / 1000.0
    valueDelta / timeDelta

}
