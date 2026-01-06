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
          case (state, (point, index)) =>
            processPoint(state, point, index)
        }
        .flatMap {
          case (_, toEmit) =>
            Stream.emits(toEmit)
        }

  private def processPoint[A](
    state: CutState[A],
    point: OutputFlightPoint[A],
    index: Long,
  ): StreamState[A] =
    state.phase match {
      case CutPhase.BeforeTakeoff =>
        handleBeforeTakeoff(state, point, index)
      case CutPhase.InFlight =>
        handleInFlight(state, point, index)
      case CutPhase.AfterLanding =>
        handleAfterLanding(state, point)
      case CutPhase.Done =>
        (state, Nil)
    }

  private def handleBeforeTakeoff[A](state: CutState[A], point: OutputFlightPoint[A], index: Long): StreamState[A] =
    val startEmitting =
      point
        .takeoff
        .map(_.lineIndex)
        .exists(t => index >= keepFrom(t))

    if startEmitting then
      val newState =
        CutState[A](
          phase = CutPhase.InFlight,
          preBuffer = Queue.empty,
          afterCount = 0,
        )

      // Transition to InFlight: emit buffered elements + current
      (newState, state.preBuffer.toList :+ point)
    else
      val updatedBuffer =
        if state.preBuffer.size >= config.bufferPoints then
          state.preBuffer.tail.enqueue(point)
        else
          state.preBuffer.enqueue(point)

      val newState = state.copy(preBuffer = updatedBuffer)

      // Keep buffering (ring buffer of last bufferPoints elements)
      (newState, Nil)

  private def handleInFlight[A](state: CutState[A], point: OutputFlightPoint[A], index: Long): StreamState[A] =
    val landingIndex   = point.landing.map(_.lineIndex)
    val isAfterLanding = landingIndex.exists(l => index > l)

    if isAfterLanding then
      val newState =
        CutState[A](
          phase = CutPhase.AfterLanding,
          preBuffer = Queue.empty,
          afterCount = 1,
        )

      // Transition to AfterLanding
      (newState, List(point))
    else
      // Still in flight - emit
      (state, List(point))

  private def handleAfterLanding[A](state: CutState[A], point: OutputFlightPoint[A]): StreamState[A] =
    if state.afterCount >= config.bufferPoints then
      val newState = state.copy(phase = CutPhase.Done)
      // Done - stop emitting
      (newState, Nil)
    else
      val newState = state.copy(afterCount = state.afterCount + 1)
      // Still emitting post-landing buffer
      (newState, List(point))

  private def keepFrom(index: Long): Long =
    Math.max(index - config.bufferPoints, 0)

  // def cut(points: FlightStagesPoints): Pipe[IO, FlysightPoint, FlysightPoint] =
  //   data =>
  //     points match {
  //       case points @ FlightStagesPoints(None, None, None, None, _, _) =>
  //         val msg = s"${YELLOW}WARNING!${RESET} Found stable data only. Might not contain a flight recording."
  //         fs2Println(points.show) >>
  //         fs2Println(msg) >>
  //         data

  //       case points @ FlightStagesPoints(Some(FlightStagePoint(takeoff, _)), _, _, None, _, _) =>
  //         val msg = s"${CYAN}No landing detected.${RESET} Collecting data from line ${keepFrom(takeoff)} until the end"
  //         fs2Println(points.show) >>
  //         retainMinPoints(points, data) {
  //           fs2Println(msg) >>
  //           data.drop(keepFrom(takeoff))
  //         }

  //       case points @ FlightStagesPoints(
  //             Some(FlightStagePoint(to, _)),
  //             _,
  //             _,
  //             Some(FlightStagePoint(landing, _)),
  //             tp,
  //             _,
  //           ) =>
  //         fs2Println(points.show) >>
  //         retainMinPoints(points, data) {
  //           val msg = s"Collecting data from line ${keepFrom(to)} to line ${keepUntil(landing, tp)}"
  //           fs2Println(msg) >>
  //           data.drop(keepFrom(to)).take(keepUntil(landing, tp))
  //         }

  //       case points @ FlightStagesPoints(None, _, _, Some(FlightStagePoint(landing, _)), totalPoints, _) =>
  //         fs2Println(points.show) >>
  //         retainMinPoints(points, data) {
  //           fs2Println(s"${CYAN}No takeoff detected.${RESET} ") >>
  //           fs2Println(s"Collecting data from line 0 to line ${keepUntil(landing, totalPoints)}") >>
  //           data.take(keepUntil(landing, totalPoints))
  //         }

  //       case flightPoints =>
  //         fs2Println(flightPoints.show) >>
  //         fs2Println(s"${RED}ERROR?${RESET} Not sure why we're here. Collecting everything, just to be sure") >>
  //         data
  //     }

  // private def retainMinPoints(
  //   flightPoints: FlightStagesPoints,
  //   data: => Stream[IO, FlysightPoint],
  // )(ifMore: => Stream[IO, FlysightPoint],
  // ): Stream[IO, FlysightPoint] =
  //   if numberOfRetainedPoints(flightPoints) < config.minRetainedPoints then
  //     fs2Println(s"${YELLOW}Too many points dropped!${RESET} Collecting all data.") >>
  //     data
  //   else
  //     ifMore
  //   end if

  // private def numberOfRetainedPoints(flightPoints: FlightStagesPoints): Double =
  //   val first =
  //     flightPoints
  //       .takeoff
  //       .fold(0L)(_.lineIndex)
  //       .toDouble

  //   val last =
  //     flightPoints
  //       .landing
  //       .fold(flightPoints.lastPoint)(_.lineIndex)
  //       .toDouble

  //   (last - first) / flightPoints.lastPoint.toDouble

  // private def keepUntil(index: Long, max: Long): Long =
  //   Math.min(index + config.bufferPoints, max)

  // private def fs2Println[A: cats.Show](a: => A): Stream[IO, Unit] =
  //   Stream.eval(IO.println(a))

  // given niceOptionShow[A: Show]: Show[Option[A]] =
  //   _.fold("N/A")(Show[A].show(_))

  // given niceDouble: Show[Double] =
  //   value => f"${value}%.2f"

  // given nicePoint: Show[FlightStagePoint] =
  //   point => s"point ${point.lineIndex.show} at altitude ${point.altitude.show}"

  // given niceFlightPoints: Show[FlightStagesPoints] =
  //   flightPoints =>
  //     s"""Flight points:
  //        |    Takeoff:  ${flightPoints.takeoff.show}
  //        |    Freefall: ${flightPoints.freefall.show}
  //        |    Canopy:   ${flightPoints.canopy.show}
  //        |    Landing:  ${flightPoints.landing.show}
  //        |""".stripMargin

}

object DataCutter {

  private type StreamState[A] = (CutState[A], List[OutputFlightPoint[A]])

  private enum CutPhase {
    case BeforeTakeoff, InFlight, AfterLanding, Done
  }

  final private case class CutState[A](
    phase: CutPhase,
    preBuffer: Queue[OutputFlightPoint[A]],
    afterCount: Long,
  )

  private object CutState {

    def apply[A](): CutState[A] =
      CutState[A](
        phase = CutPhase.BeforeTakeoff,
        preBuffer = Queue.empty[OutputFlightPoint[A]],
        afterCount = 0,
      )

  }

  def apply(): IOConfig[DataCutter] =
    IOConfig.ask.map(new DataCutter(_))

}
