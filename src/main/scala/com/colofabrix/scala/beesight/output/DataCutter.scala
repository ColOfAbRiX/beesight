package com.colofabrix.scala.beesight.output

import com.colofabrix.scala.beesight.config.*
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.output.DataCutter.*
import scala.collection.immutable.Queue

/**
 * Cuts flight data streams to retain only flight-relevant points from takeoff to landing.
 */
final class DataCutter private (config: Config) {

  /**
   * Streaming pipe that cuts data to retain only flight-relevant points
   */
  def cutPipe[F[_], A]: fs2.Pipe[F, OutputFlightPoint[A], OutputFlightPoint[A]] =
    stream =>
      stream
        .zipWithNext
        .zipWithIndex
        .mapAccumulate(CutterState[A]()) {
          case (state, ((point, Some(_)), index)) =>
            processPoint(state, point, index)
          case (state, ((point, None), index)) =>
            val (newState, toEmit) = processPoint(state, point, index)
            (newState, newState.preBuffer.toVector ++ toEmit)
        }
        .flatMap {
          case (_, pointsToEmit) => fs2.Stream.emits(pointsToEmit)
        }

  private def processPoint[A](state: CutterState[A], point: OutputFlightPoint[A], index: Long): StreamState[A] =
    state.phase match {
      case CutterPhase.BeforeTakeoff => handleBeforeTakeoff(state, point, index)
      case CutterPhase.InFlight      => handleInFlight(state, point, index)
      case CutterPhase.AfterLanding  => handleAfterLanding(state, point)
      case CutterPhase.InvalidFile   => (state, Vector(point))
      case CutterPhase.Done          => (state, Vector.empty)
    }

  private def handleBeforeTakeoff[A](state: CutterState[A], point: OutputFlightPoint[A], index: Long): StreamState[A] =
    val takeoffDetected =
      point
        .takeoff
        .map(_.index)
        .exists(t => index >= Math.max(t - config.bufferPoints, 0))

    if !point.freefall.isDefined then
      val newState = CutterState[A](CutterPhase.InvalidFile, Queue.empty)
      (newState, state.preBuffer.toVector :+ point)
    else if takeoffDetected then
      val newState = CutterState[A](CutterPhase.InFlight, Queue.empty, afterLandingCount = 0)
      (newState, state.preBuffer.toVector :+ point)
    else
      val updatedBuffer = state.preBuffer.enqueue(point)
      val newState      = state.copy(preBuffer = updatedBuffer)
      (newState, Vector.empty)

  private def handleInFlight[A](state: CutterState[A], point: OutputFlightPoint[A], index: Long): StreamState[A] =
    val isAfterLanding =
      point
        .landing
        .map(_.index)
        .exists(l => index > l)

    if isAfterLanding then
      val newState = CutterState[A](CutterPhase.AfterLanding, Queue.empty, 1)
      (newState, Vector(point))
    else
      (state, Vector(point))

  private def handleAfterLanding[A](state: CutterState[A], point: OutputFlightPoint[A]): StreamState[A] =
    if state.afterLandingCount >= config.bufferPoints then
      val newState = state.copy(phase = CutterPhase.Done)
      (newState, Vector.empty)
    else
      val newState = state.copy(afterLandingCount = state.afterLandingCount + 1)
      (newState, Vector(point))

}

object DataCutter {

  private type StreamState[A] =
    (CutterState[A], Vector[OutputFlightPoint[A]])

  private enum CutterPhase {
    case BeforeTakeoff, InFlight, AfterLanding, Done, InvalidFile
  }

  final private case class CutterState[A](
    phase: CutterPhase = CutterPhase.BeforeTakeoff,
    preBuffer: Queue[OutputFlightPoint[A]] = Queue.empty[OutputFlightPoint[A]],
    afterLandingCount: Long = 0,
  )

  /**
   * Creates a new DataCutter instance using the current configuration.
   */
  def apply(config: Config): DataCutter =
    new DataCutter(config)

}
