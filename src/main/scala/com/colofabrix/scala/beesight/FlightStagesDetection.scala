package com.colofabrix.scala.beesight

import cats.data.NonEmptyList
import cats.effect.IO
import cats.implicits.*
import com.colofabrix.scala.beesight.FlightStagesDetection.*
import com.colofabrix.scala.beesight.StreamUtils.*
import com.colofabrix.scala.beesight.config.*
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.stats.*
import com.colofabrix.scala.stats.CusumDetector.DetectorState as CusumState
import com.colofabrix.scala.stats.PhysicsDetector.DetectorState as PhysicsState
import fs2.*
import fs2.data.csv.*
import java.time.*


/**
 * Detects and analyzes different stages of a flight based on time-series data
 */
final class FlightStagesDetection(config: Config) {
  import config.detectionConfig.*

  private val cusumDetector =
    CusumDetector(30, 0.6, 15.0)

  private val physicsDetector =
    PhysicsDetector()

  /**
   * Processes a stream of flight data points to identify flight stages
   */
  def detect(data: fs2.Stream[IO, FlysightPoint]): IO[FlightStagesPoints] =
    data
      .through(detectPipe)
      .compile
      .lastOrError

  private val detectPipe: Pipe[IO, FlysightPoint, FlightStagesPoints] =
    data =>
      data
        .zipWithIndex
        .scan(StreamState()) {
          case (state, (point, i)) =>
            val time = point.time.toInstant

            val physics = physicsDetector.checkNextValue(state.physics, point.hMSL, time)

            val cusum =
              physics match {
                case PhysicsState.Empty | PhysicsState.Filling(_) =>
                  CusumState.Empty
                case PhysicsState.Detection(previous, time, value, speed, _) =>
                  cusumDetector.checkNextValue(state.cusum, speed)
              }

            StreamState(
              dataPointIndex = i,
              time = time,
              height = point.hMSL,
              physics = physics,
              cusum = cusum,
            )
        }
        .through(writeDebug)
        .fold(FlightStagesPoints.empty) {
          case (state, _) =>
            state
        }

  private def writeDebug: Pipe[IO, StreamState, StreamState] =
    data =>
      data
        .through(FileOps.writeCsv(better.files.File("debug.csv")))
        .flatMap(_ => data)

}

object FlightStagesDetection {

  final private case class StreamState(
    dataPointIndex: Long = 0,
    time: Instant = Instant.EPOCH,
    height: Double = 0.0,
    cusum: CusumState = CusumState.Empty,
    physics: PhysicsState = PhysicsState.Empty,
  )

  private object StreamState {
    import com.colofabrix.scala.beesight.csv.Encoders.*

    given csvRowEncoder: CsvRowEncoder[StreamState, String] with {
      def apply(row: StreamState): CsvRow[String] =
        CsvRow.fromNelHeaders {
          NonEmptyList
            .of(
              ("dataPointIndex", row.dataPointIndex.toString),
              ("height", formatDouble(row.height, 3)),
              ("time", formatInstant(row.time, 3)),
            )
            .appendList(selector(row.physics))
            .appendList(selector(row.cusum))
        }
    }

    /**
     * Selects the appropriate encoder based on the type of state
     */
    def selector[A](value: A): List[(String, String)] =
      value match {
        case state: CusumState   => cusumSelector(state)
        case state: PhysicsState => physicsSelector(state)
        case _                   => List.empty
      }

    /**
     * Selects the appropriate encoder for CusumState
     */
    def cusumSelector[A](state: CusumState): List[(String, String)] =
      state match {
        case _: CusumState.Empty.type => emptyProductRowEncoder[CusumState.Detection]("cusum")
        case _: CusumState.Filling    => emptyProductRowEncoder[CusumState.Detection]("cusum")
        case d: CusumState.Detection  => productRowEncoder("cusum", d)
      }

    /**
     * Selects the appropriate encoder for PhysicsState
     */
    def physicsSelector[A](state: PhysicsState): List[(String, String)] =
      state match {
        case _: PhysicsState.Empty.type => emptyProductRowEncoder[PhysicsState.Detection]("physics")
        case _: PhysicsState.Filling    => emptyProductRowEncoder[PhysicsState.Detection]("physics")
        case d: PhysicsState.Detection  => productRowEncoder("physics", d)
      }

  }

}
