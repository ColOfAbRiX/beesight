package com.colofabrix.scala.beesight

import cats.effect.IO
import cats.implicits.*
import cats.Show
import com.colofabrix.scala.beesight.config.*
import com.colofabrix.scala.beesight.DataCutter.*
import com.colofabrix.scala.beesight.model.*
import fs2.*
import scala.collection.immutable.Queue
import scala.io.AnsiColor.*

final class DataCutter private (config: Config) {

  /**
   * Streaming pipe that cuts data to retain only flight-relevant points
   */
  def cutPipe[F[_], A]: fs2.Pipe[F, OutputFlightPoint[A], OutputFlightPoint[A]] =
    stream =>
      stream
        .zipWithIndex
        .mapAccumulate(CutState[A]()) {
          case (state, (point, index)) => processPoint(state, point, index)
        }
        .flatMap {
          case (_, toEmit) => Stream.emits(toEmit)
        }

  private def processPoint[A](state: CutState[A], point: OutputFlightPoint[A], index: Long): StreamState[A] =
    state.phase match {
      case CutPhase.BeforeTakeoff => handleBeforeTakeoff(state, point, index)
      case CutPhase.InFlight      => handleInFlight(state, point, index)
      case CutPhase.AfterLanding  => handleAfterLanding(state, point)
      case CutPhase.Done          => (state, Nil)
    }

  private def handleBeforeTakeoff[A](state: CutState[A], point: OutputFlightPoint[A], index: Long): StreamState[A] =
    val startEmitting =
      point
        .takeoff
        .map(_.lineIndex)
        .exists(t => index >= keepFrom(t))

    if startEmitting then
      // Transition to InFlight: emit buffered elements + current
      val newState = CutState[A](phase = CutPhase.InFlight, preBuffer = Queue.empty, afterCount = 0)
      (newState, state.preBuffer.toList :+ point)
    else
      // Keep buffering (ring buffer of last bufferPoints elements)
      val updatedBuffer =
        if state.preBuffer.size >= config.bufferPoints then
          state.preBuffer.tail.enqueue(point)
        else
          state.preBuffer.enqueue(point)

      val newState = state.copy(preBuffer = updatedBuffer)
      (newState, Nil)

  private def handleInFlight[A](state: CutState[A], point: OutputFlightPoint[A], index: Long): StreamState[A] =
    val landingIndex   = point.landing.map(_.lineIndex)
    val isAfterLanding = landingIndex.exists(l => index > l)

    if isAfterLanding then
      // Transition to AfterLanding
      val newState = CutState[A](phase = CutPhase.AfterLanding, preBuffer = Queue.empty, afterCount = 1)
      (newState, List(point))
    else
      // Still in flight - emit
      (state, List(point))

  private def handleAfterLanding[A](state: CutState[A], point: OutputFlightPoint[A]): StreamState[A] =
    if state.afterCount >= config.bufferPoints then
      // Done - stop emitting
      val newState = state.copy(phase = CutPhase.Done)
      (newState, Nil)
    else
      // Still emitting post-landing buffer
      val newState = state.copy(afterCount = state.afterCount + 1)
      (newState, List(point))

  private def keepFrom(index: Long): Long =
    Math.max(index - config.bufferPoints, 0)

}

object DataCutter {

  private type StreamState[A] = (CutState[A], List[OutputFlightPoint[A]])

  private enum CutPhase {
    case BeforeTakeoff, InFlight, AfterLanding, Done
  }

  final private case class CutState[A](
    phase: CutPhase = CutPhase.BeforeTakeoff,
    preBuffer: Queue[OutputFlightPoint[A]] = Queue.empty[OutputFlightPoint[A]],
    afterCount: Long = 0,
  )

  def apply(): IOConfig[DataCutter] =
    IOConfig.ask.map(new DataCutter(_))

}
