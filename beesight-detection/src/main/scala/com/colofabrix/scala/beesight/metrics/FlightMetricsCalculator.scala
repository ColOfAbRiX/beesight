package com.colofabrix.scala.beesight.metrics

import cats.syntax.all.*
import com.colofabrix.scala.beesight.config.{ Altitude, MetricsConfig }
import com.colofabrix.scala.beesight.metrics.FlightMetricsCalculator.MetricsState
import com.colofabrix.scala.beesight.metrics.computations.*
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.readers.*
import com.colofabrix.scala.beesight.utils.*

/**
 * Computes per-point flight metrics.
 */
final class FlightMetricsCalculator private (config: MetricsConfig, groundElevation: Altitude) {

  private val windowCapacity: Int =
    2 * config.slopeHalfWindow + 1

  private val aerodynamics: Aerodynamics =
    Aerodynamics(config)

  /**
   * Compute the metrics for each point in the stream
   */
  def compute[F[_], A: PointReader: GpsReader]: fs2.Pipe[F, A, FlightMetric] =
    _.statefulMapEmits(initState, drainState)(advance)

  //  Windowing  //

  private def initState[A: PointReader: GpsReader](point: A): (MetricsState[A], Vector[FlightMetric]) =
    advance(point, MetricsState(Vector.empty, 0))

  private def advance[A: PointReader: GpsReader](
    point: A,
    state: MetricsState[A]
  ): (MetricsState[A], Vector[FlightMetric]) =
    val appended = state.points :+ point
    val points   = if appended.length > windowCapacity then appended.tail else appended
    val newState = MetricsState(points, state.count + 1)

    val emits =
      Option.when(points.length >= config.slopeHalfWindow + 1) {
        metric(points.length - 1 - config.slopeHalfWindow, points)
      }

    (newState, emits.toVector)

  private def drainState[A: PointReader: GpsReader](state: MetricsState[A]): Vector[FlightMetric] =
    val headIndex         = state.count - state.points.length
    val firstPendingPoint = (state.count - config.slopeHalfWindow).max(0L)
    val pendingPoints     = firstPendingPoint.until(state.count)

    pendingPoints
      .map { center =>
        val windowStart = (center - config.slopeHalfWindow).max(0L) - headIndex
        val window      = state.points.drop(windowStart.toInt)
        val centerIndex = (center - (center - config.slopeHalfWindow).max(0L)).toInt
        metric(centerIndex, window)
      }
      .toVector

  //  Metric assembly  //

  private def metric[A: PointReader: GpsReader](centerIndex: Int, window: Vector[A]): FlightMetric =
    val center    = window(centerIndex)
    val speed     = center.speed
    val elevation = center.altitude - groundElevation.value

    val horizontalAccuracy = center.horizontalAccuracy
    val verticalAccuracy   = center.verticalAccuracy

    val wmCalculator = WindowedMetrics(window)
    val acceleration = wmCalculator.acceleration
    val totalSpeed   = speed.magnitude

    val energyRate = totalSpeed * wmCalculator.totalSpeedRate - MetricsConstants.Gravity * speed.vertical

    val sphericalErrorProbably = (horizontalAccuracy, verticalAccuracy).mapN(GpsMetrics.sphericalErrorProbable)
    val courseAccuracyEstimate =
      center.speedAccuracy.map { accuracy =>
        if totalSpeed > 0.0 then Math.toDegrees(accuracy / totalSpeed)
        else 0.0
      }

    FlightMetric(
      time = center.time,
      altitude = elevation,
      verticalSpeed = speed.vertical,
      horizontalSpeed = speed.horizontal,
      totalSpeed = totalSpeed,
      course = PerPointMetrics.course(speed),
      courseRate = wmCalculator.courseRate,
      courseAccuracy = center.courseAccuracy.orElse(courseAccuracyEstimate),
      courseAccuracyEstimate = courseAccuracyEstimate,
      glideRatio = PerPointMetrics.glideRatio(speed),
      liftCoefficient = aerodynamics.liftCoefficient(acceleration, speed, center.altitude),
      dragCoefficient = aerodynamics.dragCoefficient(acceleration, speed, center.altitude),
      diveAngle = PerPointMetrics.diveAngle(speed),
      diveRate = wmCalculator.diveRate,
      turnRate = wmCalculator.turnRate(centerIndex),
      speedAccuracy = center.speedAccuracy,
      horizontalAccuracy = horizontalAccuracy,
      verticalAccuracy = verticalAccuracy,
      satellites = center.satellites,
      acceleration = acceleration,
      accelerationMagnitude = acceleration.magnitude,
      accelerationAlongTrack = PerPointMetrics.accelerationAlongTrack(acceleration, speed),
      accelerationCrossTrack = PerPointMetrics.accelerationCrossTrack(acceleration, speed),
      totalEnergy = PerPointMetrics.totalEnergy(elevation, totalSpeed),
      energyRate = energyRate,
      sphericalErrorProbably = sphericalErrorProbably,
      speedScoreAccuracy = verticalAccuracy.map(GpsMetrics.speedScoreAccuracy),
    )

}

object FlightMetricsCalculator {

  /** Rolling buffer of the most recent points, at most one full window long, plus the total point count. */
  final private[metrics] case class MetricsState[A](
    points: Vector[A],
    count: Long
  )

  def apply(config: MetricsConfig): FlightMetricsCalculator =
    new FlightMetricsCalculator(config, Altitude(0.0))

  def apply(config: MetricsConfig, groundElevation: Altitude): FlightMetricsCalculator =
    new FlightMetricsCalculator(config, groundElevation)

  def apply(config: MetricsConfig, groundElevation: Option[Altitude]): FlightMetricsCalculator =
    new FlightMetricsCalculator(config, groundElevation.getOrElse(Altitude(0.0)))

}
