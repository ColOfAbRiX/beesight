package com.colofabrix.scala.beesight.metrics

import cats.syntax.all.*
import com.colofabrix.scala.beesight.config.MetricsConfig
import com.colofabrix.scala.beesight.metrics.FlightMetricsCalculator.MetricsState
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.readers.*
import com.colofabrix.scala.beesight.utils.*

/**
 * Computes per-point flight metrics replicating the FlySight Viewer main-plot curves.
 *
 * Every input point produces one [[FlightMetric]]. Windowed metrics use a least-squares slope over a clamped
 * ±`slopeHalfWindow` window (9 samples by default), replicating FlySight's `getSlope`.
 */
final class FlightMetricsCalculator private (config: MetricsConfig, groundElevation: Double) {

  private val windowCapacity: Int = 2 * config.slopeHalfWindow + 1

  private val aerodynamics: Aerodynamics = Aerodynamics(config)

  /** Streaming pipe: one FlightMetric per input point. */
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
    val diveAngle = PerPointMetrics.diveAngle(speed)

    val horizontalAccuracy     = center.horizontalAccuracy
    val verticalAccuracy       = center.verticalAccuracy
    val acceleration           = WindowedMetrics.acceleration(window)
    val totalSpeed             = speed.magnitude
    val totalSpeedRate         = WindowedMetrics.totalSpeedRate(window)
    val sphericalErrorProbably = (horizontalAccuracy, verticalAccuracy).mapN(GpsMetrics.sphericalErrorProbable)
    val elevation              = center.altitude - groundElevation

    FlightMetric(
      time = center.time,
      altitude = elevation,
      verticalSpeed = speed.vertical,
      horizontalSpeed = speed.horizontal,
      totalSpeed = totalSpeed,
      course = PerPointMetrics.course(speed),
      courseRate = WindowedMetrics.courseRate(window),
      courseAccuracy = center.courseAccuracy,
      glideRatio = PerPointMetrics.glideRatio(speed),
      liftCoefficient = aerodynamics.liftCoefficient(diveAngle, totalSpeed, center.altitude),
      dragCoefficient = aerodynamics.dragCoefficient(diveAngle, totalSpeed, center.altitude),
      diveAngle = diveAngle,
      diveRate = WindowedMetrics.diveRate(window),
      turnRate = WindowedMetrics.turnRate(centerIndex, window),
      speedAccuracy = center.speedAccuracy,
      horizontalAccuracy = horizontalAccuracy,
      verticalAccuracy = verticalAccuracy,
      satellites = center.satellites,
      acceleration = acceleration,
      accelerationMagnitude = acceleration.magnitude,
      accelerationAlongTrack = PerPointMetrics.accelerationAlongTrack(acceleration, speed),
      accelerationCrossTrack = PerPointMetrics.accelerationCrossTrack(acceleration, speed),
      totalEnergy = PerPointMetrics.totalEnergy(elevation, totalSpeed),
      energyRate = totalSpeed * totalSpeedRate - MetricsConstants.Gravity * speed.vertical,
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

  def apply(config: MetricsConfig, groundElevation: Double = 0.0): FlightMetricsCalculator =
    new FlightMetricsCalculator(config, groundElevation)

}
