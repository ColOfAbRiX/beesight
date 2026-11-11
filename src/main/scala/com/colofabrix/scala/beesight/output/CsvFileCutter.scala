package com.colofabrix.scala.beesight.output

import com.colofabrix.scala.beesight.config.*
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.output.CsvFileCutter.*
import scala.collection.immutable.Queue
import com.colofabrix.scala.beesight.IOConfig

/**
 * Cuts flight data streams to retain only flight-relevant points from takeoff to landing.
 */
final private[beesight] class CsvFileCutter private (config: Config) {

  /**
   * Streaming pipe that cuts data to retain only flight-relevant points
   */
  def cutPipe[F[_], A]: fs2.Pipe[F, RichRow[A], (FlightEvents, A)] =
    stream =>
      stream
        .fold(Vector.empty[RichRow[A]])(_ :+ _)
        .flatMap { allPoints => 
          fs2.Stream.emits(cutVector(allPoints).retained)
        }

  /**
   * Cuts the given points and reports what was kept, for the metadata file.
   */
  def cutVector[A](points: Vector[RichRow[A]]): CutResult[A] = {
    val cut     = applyCutting(points)
    val ratio   = if points.isEmpty then 1.0 else cut.size.toDouble / points.size
    val applied = ratio >= config.dataCutterConfig.minRetainedPoints.value

    val retained =
      if applied then cut
      else points.zipWithIndex.map { case (row, index) => (index.toLong, row) }

    CutResult(
      summary = CutSummary(
        applied = applied,
        totalPoints = points.size.toLong,
        retainedPoints = retained.size.toLong,
        firstRetainedIndex = retained.headOption.fold(0L)(_._1),
        lastRetainedIndex = retained.lastOption.fold(0L)(_._1),
      ),
      retained = retained.map(_._2),
    )
  }

  private def applyCutting[A](points: Vector[RichRow[A]]): Vector[Retained[A]] =
    fs2.Stream
      .emits(points)
      .zipWithNext
      .zipWithIndex
      .mapAccumulate(CutterState.empty[A]) {
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
      case CutterPhase.AfterLanding  => handleAfterLanding(state, row, index)
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
      (newState, keptPoints :+ ((index, row)))
    else if landingPassed then
      val keptPoints                = state.preBuffer.toVector
      val newState                  = CutterState[A](CutterPhase.AfterLanding, Queue.empty, afterLandingCount = 0)
      val (afterState, afterPoints) = handleAfterLanding(newState, row, index)
      (afterState, keptPoints ++ afterPoints)
    else
      val updatedBuffer = state.preBuffer.enqueue((index, row))
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
      handleAfterLanding(newState, row, index)
    else
      (state, Vector((index, row)))

  private def handleAfterLanding[A](state: CutterState[A], row: RichRow[A], index: Long): StreamState[A] =
    if state.afterLandingCount >= config.dataCutterConfig.keepAfterLanding.value then
      val newState = state.copy(phase = CutterPhase.Done)
      (newState, Vector.empty)
    else
      val newState = state.copy(afterLandingCount = state.afterLandingCount + 1)
      (newState, Vector((index, row)))

}

private[beesight] object CsvFileCutter {

  /** What the cut operation kept from the input, reported to the metadata file. */
  final case class CutSummary(
    applied: Boolean,
    totalPoints: Long,
    retainedPoints: Long,
    firstRetainedIndex: Long,
    lastRetainedIndex: Long,
  )

  /** The rows kept by the cut, with the summary of the operation. */
  final case class CutResult[A](
    summary: CutSummary,
    retained: Vector[RichRow[A]],
  )

  private type RichRow[A] = 
    (FlightEvents, A)

  /** A retained row together with its zero-based position in the input data. */
  private type Retained[A] = 
    (Long, RichRow[A])

  private type StreamState[A] =
    (CutterState[A], Vector[Retained[A]])

  private enum CutterPhase {
    case BeforeTakeoff
    case InFlight
    case AfterLanding
    case Done
  }

  final private case class CutterState[A](
    phase: CutterPhase,
    preBuffer: Queue[Retained[A]],
    afterLandingCount: Long,
  )

  private object CutterState {

    def empty[A]: CutterState[A] =
      CutterState(CutterPhase.BeforeTakeoff, Queue.empty[Retained[A]], 0)

  }

  def apply(config: Config): CsvFileCutter =
    new CsvFileCutter(config)

  def apply(): IOConfig[CsvFileCutter] =
    IOConfig.ask.map(config => new CsvFileCutter(config))

}
