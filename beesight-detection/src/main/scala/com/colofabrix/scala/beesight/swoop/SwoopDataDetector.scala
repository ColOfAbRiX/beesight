package com.colofabrix.scala.beesight.swoop

import cats.effect.kernel.Concurrent
import cats.syntax.all.*
import com.colofabrix.scala.beesight.config.SwoopConfig
import com.colofabrix.scala.beesight.math.{ Heading, Stats }
import com.colofabrix.scala.beesight.model.*
import java.lang.Math.*
import java.time.Duration

/**
 * Computes swoop metrics from the canopy-to-landing dataPoints of a flight.
 */
final class SwoopDataDetector private (config: SwoopConfig) {

  /**
   * Detects swoop metrics from the flight data stream.
   */
  def detect[F[_]: Concurrent, A: PointReader](events: FlightEvents, stream: fs2.Stream[F, A]): F[Option[SwoopData]] =
    (events.canopy, events.landing)
      .mapN(detect(_, _, stream))
      .sequence
      .map(_.flatten)

  private def detect[F[_]: Concurrent, A: PointReader](
    canopy: FlightEventPoint,
    landing: FlightEventPoint,
    stream: fs2.Stream[F, A]
  ): F[Option[SwoopData]] =
    stream
      .zipWithIndex
      .drop(canopy.index)
      .take(landing.index - canopy.index + 1)
      .map(IndexedPoint.fromZip)
      .compile
      .toVector
      .map(computeSwoopMetrics)

  //  Swoop Detection  //

  private def computeSwoopMetrics(dataPoints: Vector[IndexedPoint]): Option[SwoopData] =
    for {
      rolloutStartIdx <- findRolloutStart(dataPoints) if isValidRolloutStart(dataPoints, rolloutStartIdx)
      turnInitIdx     <- findTurnInitiation(dataPoints, rolloutStartIdx)
      result           = buildSwoopData(dataPoints, turnInitIdx, rolloutStartIdx)
    } yield result

  private def isValidRolloutStart(dataPoints: Vector[IndexedPoint], idx: Int): Boolean =
    dataPoints(idx).point.speed.vertical >= config.minSwoopVerticalSpeed

  private def findRolloutStart(dataPoints: Vector[IndexedPoint]): Option[Int] =
    if dataPoints.length < 2 then
      None
    else
      val indicesBeforeLanding    = dataPoints.indices.init.reverse
      val landingVz               = dataPoints.last.point.speed.vertical
      val (_, rolloutStartIdx, _) =
        indicesBeforeLanding.foldLeft((landingVz, dataPoints.length - 1, true)) {
          case ((nextVz, peakIdx, stillMonotonic), i) =>
            if !stillMonotonic then
              (nextVz, peakIdx, false)
            else
              val currentVz = dataPoints(i).point.speed.vertical
              if currentVz >= nextVz then (currentVz, i, true)
              else (nextVz, peakIdx, false)
        }

      Option.when(rolloutStartIdx < dataPoints.length - 1)(rolloutStartIdx)

  private def findTurnInitiation(dataPoints: Vector[IndexedPoint], rolloutStartIdx: Int): Option[Int] =
    if rolloutStartIdx < 4 then None
    else Stats.findInflectionBy(dataPoints.take(rolloutStartIdx))(_.point.speed.vertical)

  //  Metrics Computation  //

  /** Assembles all swoop metrics from the identified turn phase. */
  private def buildSwoopData(dataPoints: Vector[IndexedPoint], turnInitIdx: Int, rolloutStartIdx: Int): SwoopData =
    val turnPhaseSpeeds =
      dataPoints
        .slice(turnInitIdx, rolloutStartIdx + 1)
        .map(_.point.speed)

    val turnAndRolloutSpeeds =
      dataPoints
        .drop(turnInitIdx)
        .map(_.point.speed)

    val turnInit = dataPoints(turnInitIdx)
    val rollout  = dataPoints(rolloutStartIdx)

    val degreesOfRotation = Heading.cumulativeHeadingChange(turnPhaseSpeeds, config.minHorizontalSpeed)

    SwoopData(
      turnInitiation = toSwoopEventPoint(turnInit),
      rolloutStart = toSwoopEventPoint(rollout),
      maxVerticalSpeed = turnPhaseSpeeds.map(_.vertical).max,
      maxTotalSpeed = turnPhaseSpeeds.map(_.magnitude).max,
      maxHorizontalSpeed = turnAndRolloutSpeeds.map(_.horizontal).max,
      degreesOfRotation = degreesOfRotation,
      timeToExecuteTurn = Duration.between(turnInit.point.time, rollout.point.time),
    )

  private def toSwoopEventPoint(ip: IndexedPoint): SwoopEventPoint =
    SwoopEventPoint(
      index = ip.idx,
      altitude = ip.point.altitude,
      time = ip.point.time,
      speed = ip.point.speed,
    )

}

object SwoopDataDetector {

  def apply(config: SwoopConfig): SwoopDataDetector =
    new SwoopDataDetector(config)

}
