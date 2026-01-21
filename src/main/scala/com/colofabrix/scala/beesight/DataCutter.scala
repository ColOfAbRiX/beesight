package com.colofabrix.scala.beesight

import cats.effect.IO
import cats.implicits.*
import com.colofabrix.scala.beesight.config.*
import com.colofabrix.scala.beesight.DataCutter.*
import com.colofabrix.scala.beesight.model.*
import fs2.*
import scala.collection.immutable.Queue

final class DataCutter private (config: Config) {

  /**
   * Streaming pipe that cuts data to retain only flight-relevant points
   */
  def cutPipe[F[_], A]: fs2.Pipe[F, OutputFlightRow[A], OutputFlightRow[A]] =
    stream =>
      stream
        .zipWithNext
        .zipWithIndex
        .mapAccumulate(CutState[A]()) {
          case (state, ((point, Some(_)), index)) =>
            // Process each element
            processPoint(state, point, index)
          case (state, ((point, None), index)) =>
            // Emit potential buffer before the last element
            val (newState, toEmit) = processPoint(state, point, index)
            (newState, newState.preBuffer.toVector ++ toEmit)
        }
        .flatMap {
          case (state, pointsToEmit) => Stream.emits(pointsToEmit)
        }

  private def processPoint[A](state: CutState[A], point: OutputFlightRow[A], index: Long): StreamState[A] =
    state.phase match {
      case CutPhase.BeforeTakeoff => handleBeforeTakeoff(state, point, index)
      case CutPhase.InFlight      => handleInFlight(state, point, index)
      case CutPhase.AfterLanding  => handleAfterLanding(state, point)
      case CutPhase.InvalidFile   => (state, Vector(point))
      case CutPhase.Done          => (state, Vector.empty)
    }

  private def handleBeforeTakeoff[A](state: CutState[A], point: OutputFlightRow[A], index: Long): StreamState[A] =
    val takeoffDetected =
      point
        .takeoff
        .map(_.lineIndex)
        .exists(t => index >= Math.max(t - config.bufferPoints, 0))

    if !point.isValid then
      // Transition to Invalid: emit buffered elements + current
      val newState = CutState[A](phase = CutPhase.InvalidFile, preBuffer = Queue.empty)
      (newState, state.preBuffer.toVector :+ point)
    else if takeoffDetected then
      // Transition to InFlight: emit buffered elements + current
      val newState   = CutState[A](phase = CutPhase.InFlight, preBuffer = Queue.empty, afterLandingCount = 0)
      val emitPoints = state.preBuffer.take(config.bufferPoints).toVector :+ point
      (newState, state.preBuffer.toVector :+ point)
    else
      // Keep buffering
      val updatedBuffer = state.preBuffer.enqueue(point)
      val newState      = state.copy(preBuffer = updatedBuffer)
      (newState, Vector.empty)

  private def handleInFlight[A](state: CutState[A], point: OutputFlightRow[A], index: Long): StreamState[A] =
    val isAfterLanding =
      point
        .landing
        .map(_.lineIndex)
        .exists(l => index > l)

    if isAfterLanding then
      // Transition to AfterLanding
      val newState = CutState[A](phase = CutPhase.AfterLanding, preBuffer = Queue.empty, afterLandingCount = 1)
      (newState, Vector(point))
    else
      // Still in flight - emit
      (state, Vector(point))

  private def handleAfterLanding[A](state: CutState[A], point: OutputFlightRow[A]): StreamState[A] =
    if state.afterLandingCount >= config.bufferPoints then
      // Done - stop emitting
      val newState = state.copy(phase = CutPhase.Done)
      (newState, Vector.empty)
    else
      // Still emitting post-landing buffer
      val newState = state.copy(afterLandingCount = state.afterLandingCount + 1)
      (newState, Vector(point))

}

object DataCutter {

  private type StreamState[A] =
    (CutState[A], Vector[OutputFlightRow[A]])

  private enum CutPhase {
    case BeforeTakeoff, InFlight, AfterLanding, Done, InvalidFile
  }

  final private case class CutState[A](
    phase: CutPhase = CutPhase.BeforeTakeoff,
    preBuffer: Queue[OutputFlightRow[A]] = Queue.empty[OutputFlightRow[A]],
    afterLandingCount: Long = 0,
  )

  def apply(): IOConfig[DataCutter] =
    IOConfig.ask.map(new DataCutter(_))

}
