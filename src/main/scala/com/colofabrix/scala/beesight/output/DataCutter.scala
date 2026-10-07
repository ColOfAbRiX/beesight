package com.colofabrix.scala.beesight.output

import com.colofabrix.scala.beesight.config.*
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.output.DataCutter.*
import scala.collection.immutable.Queue

/**
 * Cuts flight data streams to retain only flight-relevant points from takeoff to landing.
 */
final private[beesight] class DataCutter private (config: Config) {

  /**
   * Streaming pipe that cuts data to retain only flight-relevant points
   */
  def cutPipe[F[_], A]: fs2.Pipe[F, RichRow[A], (FlightEvents, A)] =
    stream =>
      stream
        .fold(Vector.empty[RichRow[A]])(_ :+ _)
        .flatMap { allPoints =>
          val cutResult     = applyCutting(allPoints)
          val retainedRatio =
            if allPoints.isEmpty then 1.0
            else cutResult.size.toDouble / allPoints.size
          if retainedRatio < config.dataCutterConfig.minRetainedPoints.value then
            fs2.Stream.emits(allPoints)
          else
            fs2.Stream.emits(cutResult)
        }

  private def applyCutting[A](points: Vector[RichRow[A]]): Vector[RichRow[A]] =
    fs2.Stream
      .emits(points)
      .zipWithNext
      .zipWithIndex
      .mapAccumulate(CutterState[A]()) {
        case (state, ((row, Some(_)), index)) =>
          processPoint(state, row, index)
        case (state, ((row, None), index)) =>
          val (newState, toEmit) = processPoint(state, row, index)
          (newState, newState.preBuffer.toVector ++ toEmit)
      }
      .flatMap { case (_, pointsToEmit) => fs2.Stream.emits(pointsToEmit) }
      .toVector

  private def processPoint[A](state: CutterState[A], row: RichRow[A], index: Long): StreamState[A] =
    state.phase match {
      case CutterPhase.BeforeTakeoff => handleBeforeTakeoff(state, row, index)
      case CutterPhase.InFlight      => handleInFlight(state, row, index)
      case CutterPhase.AfterLanding  => handleAfterLanding(state, row)
      case CutterPhase.Done          => (state, Vector.empty)
    }

  private def handleBeforeTakeoff[A](state: CutterState[A], row: RichRow[A], index: Long): StreamState[A] =
    val takeoffDetected =
      row
        ._1
        .takeoff
        .map(_.index)
        .exists(t => index >= t)

    val landingPassed =
      row._1.takeoff.isEmpty &&
      row
        ._1
        .landing
        .map(_.index)
        .exists(l => index > l)

    if takeoffDetected then
      val keep       = config.dataCutterConfig.keepBeforeTakeoff.value
      val keptPoints = state.preBuffer.toVector.takeRight(keep)
      val newState   = CutterState[A](CutterPhase.InFlight, Queue.empty, afterLandingCount = 0)
      (newState, keptPoints :+ row)
    else if landingPassed then
      val keptPoints                = state.preBuffer.toVector
      val newState                  = CutterState[A](CutterPhase.AfterLanding, Queue.empty, afterLandingCount = 0)
      val (afterState, afterPoints) = handleAfterLanding(newState, row)
      (afterState, keptPoints ++ afterPoints)
    else
      val updatedBuffer = state.preBuffer.enqueue(row)
      val newState      = state.copy(preBuffer = updatedBuffer)
      (newState, Vector.empty)

  private def handleInFlight[A](state: CutterState[A], row: RichRow[A], index: Long): StreamState[A] =
    val isAfterLanding =
      row
        ._1
        .landing
        .map(_.index)
        .exists(l => index > l)

    if isAfterLanding then
      val newState = CutterState[A](CutterPhase.AfterLanding, Queue.empty, 0)
      handleAfterLanding(newState, row)
    else
      (state, Vector(row))

  private def handleAfterLanding[A](state: CutterState[A], row: RichRow[A]): StreamState[A] =
    if state.afterLandingCount >= config.dataCutterConfig.keepAfterLanding.value then
      val newState = state.copy(phase = CutterPhase.Done)
      (newState, Vector.empty)
    else
      val newState = state.copy(afterLandingCount = state.afterLandingCount + 1)
      (newState, Vector(row))

}

private[beesight] object DataCutter {

  private type RichRow[A] = (FlightEvents, A)

  private type StreamState[A] =
    (CutterState[A], Vector[RichRow[A]])

  private enum CutterPhase {
    case BeforeTakeoff, InFlight, AfterLanding, Done
  }

  final private case class CutterState[A](
    phase: CutterPhase = CutterPhase.BeforeTakeoff,
    preBuffer: Queue[RichRow[A]] = Queue.empty[RichRow[A]],
    afterLandingCount: Long = 0,
  )

  def apply(config: Config): DataCutter =
    new DataCutter(config)

}
