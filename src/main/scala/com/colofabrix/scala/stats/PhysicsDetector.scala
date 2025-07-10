package com.colofabrix.scala.stats

import breeze.linalg.*
import breeze.stats.*
import cats.effect.IO
import com.colofabrix.scala.stats.PhysicsDetector.*
import java.lang.Math.*
import scala.collection.immutable.Queue
import java.time.OffsetDateTime

/**
 * PhysicsDetector
 */
final class PhysicsDetector private (windowSize: Int):

  def checkNextValue(state: DetectorState, value: Double, time: OffsetDateTime): DetectorState =
    checkNextValue(state, TimedValue(value, time))

  def checkNextValue(state: DetectorState, currentValue: TimedValue): DetectorState =
    state match {
      case DetectorState.Empty =>
        DetectorState.Filling(Queue(currentValue))
      case DetectorState.Filling(window) if window.length < windowSize =>
        DetectorState.Filling(window.enqueue(currentValue))
      case DetectorState.Filling(previousWindow) =>
        nextState(previousWindow, currentValue)
      case DetectorState.Detection(previousWindow, _, _, _) =>
        nextState(previousWindow, currentValue)
    }

  private def nextState(previousWindow: Queue[TimedValue], value: TimedValue): DetectorState =
    val currentWindow = pushToWindow(value, previousWindow)
    val currentSpeed  = windowDerivative(currentWindow, previousWindow)

    DetectorState.Detection(
      valuesWindow = currentWindow,
      time = value.time,
      value = value.value,
      speed = currentSpeed,
    )

  private def windowDerivative(current: Queue[TimedValue], previous: Queue[TimedValue]): Double =
    val avgCurrentValue = mean(current.map(_.value))
    val avgPreviousValue = mean(previous.map(_.value))

    val currentTime = current.last.time
    val previousTime = previous.last.time

    val avgCurrent = TimedValue(avgCurrentValue, currentTime)
    val avgPrevious = TimedValue(avgPreviousValue, previousTime)

    derivative(avgCurrent, avgPrevious)

  private def derivative(currentValue: TimedValue, previousValue: TimedValue): Double =
    val valueDelta = currentValue.value - previousValue.value
    val timeDelta  = java.time.Duration.between(previousValue.time, currentValue.time).toMillis / 1000.0
    valueDelta / timeDelta

  private def pushToWindow(value: TimedValue, queue: Queue[TimedValue]): Queue[TimedValue] =
    queue.dequeue._2.enqueue(value)

/**
 * PhysicsDetector
 */
object PhysicsDetector {

  def apply(windowSize: Int): PhysicsDetector =
    new PhysicsDetector(Math.max(windowSize, 1))

  final case class TimedValue(value: Double, time: OffsetDateTime)

  enum DetectorState {

    case Empty extends DetectorState

    case Filling(window: Queue[TimedValue]) extends DetectorState

    case Detection(
      valuesWindow: Queue[TimedValue],
      time: OffsetDateTime,
      value: Double,
      speed: Double,
    ) extends DetectorState

  }

}
