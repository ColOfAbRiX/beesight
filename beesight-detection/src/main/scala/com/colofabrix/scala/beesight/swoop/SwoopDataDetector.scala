package com.colofabrix.scala.beesight.swoop

import cats.effect.kernel.Concurrent
import cats.syntax.all.*
import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.math.{ Heading, Stats }
import com.colofabrix.scala.beesight.model.*
import java.lang.Math.*
import java.time.Duration
import com.colofabrix.scala.beesight.preprocess.PreprocessData

/**
 * Computes swoop metrics from the canopy-to-landing dataPoints of a flight.
 */
final class SwoopDataDetector private (config: DetectionConfig) {

  //  Dependencies  //

  private val preprocessor: PreprocessData =
    PreprocessData(config)

  /**
   * Detects swoop metrics from the flight data stream.
   */
  def detect[F[_]: Concurrent, A: PointReader](events: FlightEvents, stream: fs2.Stream[F, A]): F[Option[SwoopData]] =
    val canopy  = events.canopy
    val landing = events.landing
    println(s"[DEBUG] canopy=$canopy, landing=$landing")
    (canopy, landing)
      .mapN(detect(_, _, stream))
      .sequence
      .map { result =>
        println(s"[DEBUG] inner detect result=$result")
        result.flatten
      }

  private def detect[F[_]: Concurrent, A: PointReader](
    canopy: EventPoint,
    landing: EventPoint,
    stream: fs2.Stream[F, A]
  ): F[Option[SwoopData]] =
    stream
      .through(preprocessor.preprocess)
      .zipWithIndex
      .drop(canopy.index)
      .take(landing.index - canopy.index + 1)
      .map(IndexedPoint.fromZip)
      .compile
      .toVector
      .map(computeSwoopMetrics)

  //  Swoop Detection  //

  private def computeSwoopMetrics(dataPoints: Vector[IndexedPoint]): Option[SwoopData] =
    println(s"[DEBUG] computeSwoopMetrics: segment length=${dataPoints.length}")
    if dataPoints.nonEmpty then
      println(s"[DEBUG] first point: idx=${dataPoints.head.idx}, alt=${dataPoints.head.point.altitude}, vz=${dataPoints.head.point.speed.vertical}")
      println(s"[DEBUG] last point:  idx=${dataPoints.last.idx}, alt=${dataPoints.last.point.altitude}, vz=${dataPoints.last.point.speed.vertical}")
      // Find the global max vz and where it is
      val maxVz    = dataPoints.map(_.point.speed.vertical).max
      val maxVzIdx = dataPoints.indexWhere(_.point.speed.vertical == maxVz)
      println(s"[DEBUG] max vz=$maxVz at segment index=$maxVzIdx (absolute idx=${dataPoints(maxVzIdx).idx}, alt=${dataPoints(maxVzIdx).point.altitude})")
    val rollout = findRolloutStart(dataPoints)
    println(s"[DEBUG] findRolloutStart=$rollout")
    rollout.foreach { idx =>
      println(s"[DEBUG] rollout candidate at $idx: vz=${dataPoints(idx).point.speed.vertical}, minSwoopVerticalSpeed=${config.swoop.minSwoopVerticalSpeed}")
      // Show nearby points for context
      val start = math.max(0, idx - 5)
      val end   = math.min(dataPoints.length - 1, idx + 5)
      (start to end).foreach { i =>
        println(s"[DEBUG]   [$i] vz=${dataPoints(i).point.speed.vertical}")
      }
    }
    val validRollout = rollout.filter(idx => isValidRolloutStart(dataPoints, idx))
    println(s"[DEBUG] isValidRolloutStart=$validRollout")
    val turnInit = validRollout.flatMap(idx => findTurnInitiation(dataPoints, idx))
    println(s"[DEBUG] findTurnInitiation=$turnInit")
    for {
      rolloutStartIdx <- validRollout
      turnInitIdx     <- turnInit
      result           = buildSwoopData(dataPoints, turnInitIdx, rolloutStartIdx)
    } yield result

  private def isValidRolloutStart(dataPoints: Vector[IndexedPoint], idx: Int): Boolean =
    dataPoints(idx).point.speed.vertical >= config.swoop.minSwoopVerticalSpeed

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

    val degreesOfRotation = Heading.cumulativeHeadingChange(turnPhaseSpeeds, config.swoop.minHorizontalSpeed)

    SwoopData(
      turnInitiation = EventPoint(turnInit.idx, turnInit.point.altitude),
      rolloutStart = EventPoint(rollout.idx, rollout.point.altitude),
      maxVerticalSpeed = turnPhaseSpeeds.map(_.vertical).max,
      maxTotalSpeed = turnPhaseSpeeds.map(_.magnitude).max,
      maxHorizontalSpeed = turnAndRolloutSpeeds.map(_.horizontal).max,
      degreesOfRotation = degreesOfRotation,
      timeToExecuteTurn = Duration.between(turnInit.point.time, rollout.point.time),
    )

}

object SwoopDataDetector {

  def apply(config: DetectionConfig): SwoopDataDetector =
    new SwoopDataDetector(config)

}
