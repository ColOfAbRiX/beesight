package com.colofabrix.scala.stats

import breeze.linalg.*
import breeze.stats.*
import cats.effect.IO
import com.colofabrix.scala.stats.Calculus.TimedValue
import com.colofabrix.scala.stats.PhysicsCalculator.*
import java.lang.Math.*
import java.time.Instant
import scala.collection.immutable.Queue

/**
 * Detector that calculates physical properties (speed and acceleration) from time-series data
 */
final class PhysicsCalculator private ():

  /**
   * Processes a new value with its timestamp and updates the detector state
   */
  def checkNextValue(state: DetectorState, value: Double, time: Instant): DetectorState =
    checkNextValue(state, TimedValue(value, time))

  /**
   * Processes a new timed value and updates the detector state
   */
  def checkNextValue(state: DetectorState, currentValue: TimedValue): DetectorState =
    state match {
      case DetectorState.Empty =>
        DetectorState.Filling(Queue(currentValue))

      case DetectorState.Filling(previousValues) if previousValues.length < 2 =>
        DetectorState.Filling(pushToQueue(currentValue, previousValues))

      case DetectorState.Filling(previousValues) =>
        nextState(previousValues, currentValue)

      case DetectorState.Result(previousValues, _, _, _, _) =>
        nextState(previousValues, currentValue)
    }

  private def nextState(previousValues: Queue[TimedValue], currentValue: TimedValue): DetectorState =
    val previous1Value = previousValues(1)
    val previous2Value = previousValues(0)

    val currentSpeed  = TimedValue(Calculus.derivative(previous1Value, currentValue), currentValue.time)
    val previousSpeed = TimedValue(Calculus.derivative(previous2Value, previous1Value), previous1Value.time)

    val currentAcceleration = Calculus.derivative(previousSpeed, currentSpeed)

    val updatedPrevious = pushToQueue(currentValue, previousValues)

    DetectorState.Result(
      previous = updatedPrevious,
      time = currentValue.time,
      value = currentValue.value,
      speed = currentSpeed.value,
      acceleration = currentAcceleration,
    )

  private def pushToQueue(value: TimedValue, queue: Queue[TimedValue]): Queue[TimedValue] =
    if queue.size < 2 then
      queue.enqueue(value)
    else
      queue.dequeue._2.enqueue(value)

/**
 * Factory and companion object for PhysicsDetector with state definitions
 */
object PhysicsCalculator {

  /**
   * Creates a new PhysicsDetector instance
   */
  def apply(): PhysicsCalculator =
    new PhysicsCalculator()

  /**
   * Represents the different states of the physics detector
   */
  enum DetectorState {

    /**
     * Initial state with no data points
     */
    case Empty extends DetectorState

    /**
     * State where the detector is collecting initial data points
     */
    case Filling(values: Queue[TimedValue]) extends DetectorState

    /**
     * State where the detector has enough data to calculate speed and acceleration
     */
    case Result(
      previous: Queue[TimedValue],
      time: Instant,
      value: Double,
      speed: Double,
      acceleration: Double,
    ) extends DetectorState

  }

}
