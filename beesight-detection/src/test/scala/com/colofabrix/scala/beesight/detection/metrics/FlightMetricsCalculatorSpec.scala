package com.colofabrix.scala.beesight.detection.metrics

import cats.effect.IO
import com.colofabrix.scala.beesight.IOValues
import com.colofabrix.scala.beesight.detection.config.{ Area, Mass, MetricsConfig }
import com.colofabrix.scala.beesight.detection.model.{ FlightMetric, * }
import com.colofabrix.scala.beesight.detection.readers.*
import java.time.Instant
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class FlightMetricsCalculatorSpec extends AnyFreeSpec with Matchers with IOValues {

  private val GpsFix =
    Gps(1.0, 2.0, 0.5, Some(3.0), 11)

  private val defaultConfig =
    MetricsConfig(slopeHalfWindow = 4, mass = Mass(90.0), planformArea = Area(2.0))

  "FlightMetricsCalculator" - {

    "emit one metric per input point, in input order" in {
      val points = (0 until 12).map(index => pointAt(index, north = 30.0, vertical = 10.0))

      val results = compute(points, defaultConfig)

      results.size shouldBe points.size
      results.map(_.time.toEpochMilli) shouldBe points.map(_.time.toEpochMilli)
    }

    "compute the metrics of a steady descent" in {
      val points = (0 until 9).map(index =>
        pointAt(index, north = 30.0, vertical = 10.0, altitude = 1000.0 - 5.0 * index)
      )

      val results = compute(points, defaultConfig)
      val metric  = results(4)

      metric.verticalSpeed shouldBe 10.0
      metric.horizontalSpeed shouldBe 30.0
      metric.totalSpeed shouldBe Math.sqrt(1000.0) +- 1e-9
      metric.diveAngle shouldBe Math.toDegrees(Math.atan2(10.0, 30.0)) +- 1e-9
      metric.glideRatio shouldBe 3.0
      metric.course shouldBe 0.0 +- 1e-9
      metric.courseRate shouldBe 0.0 +- 1e-9
      metric.diveRate shouldBe 0.0 +- 1e-9
      metric.turnRate shouldBe 0.0 +- 1e-9
      metric.acceleration shouldBe GeoVector.zero
      metric.accelerationMagnitude shouldBe 0.0 +- 1e-9
      metric.accelerationAlongTrack shouldBe 0.0 +- 1e-9
      metric.accelerationCrossTrack shouldBe 0.0 +- 1e-9
      metric.totalEnergy shouldBe (1000.0 / 2.0 + 9.80665 * 980.0) +- 1e-9
      metric.energyRate shouldBe -98.0665 +- 1e-6
    }

    "compute the lift and drag coefficients of a steady descent" in {
      val points = (0 until 9).map(index =>
        pointAt(index, north = 30.0, vertical = 10.0, altitude = 1000.0 - 5.0 * index)
      )

      val results = compute(points, defaultConfig)
      val metric  = results(4)

      val mass         = defaultConfig.mass.value
      val planformArea = defaultConfig.planformArea.value
      val totalSpeed   = Math.sqrt(1000.0)

      // The windowed acceleration is zero, so only gravity remains on the vertical component
      val accelD          = 0.0 - 9.80665
      val proj            = accelD * 10.0 / totalSpeed
      val dragNorth       = proj * 30.0 / totalSpeed
      val dragDown        = proj * 10.0 / totalSpeed
      val accelDrag       = Math.sqrt(dragNorth * dragNorth + dragDown * dragDown)
      val liftNorth       = 0.0 - dragNorth
      val liftDown        = accelD - dragDown
      val accelLift       = Math.sqrt(liftNorth * liftNorth + liftDown * liftDown)
      val pressure        = 101325.0 * Math.pow(1.0 - 0.0065 * 980.0 / 288.15, 9.80665 * 0.0289644 / 8.31447 / 0.0065)
      val airDensity      = pressure / (8.31447 / 0.0289644) / (288.15 - 0.0065 * 980.0)
      val dynamicPressure = airDensity * totalSpeed * totalSpeed / 2.0

      metric.liftCoefficient shouldBe (mass * accelLift / dynamicPressure / planformArea) +- 1e-9
      metric.dragCoefficient shouldBe (mass * accelDrag / dynamicPressure / planformArea) +- 1e-9
    }

    "compute the acceleration of a linear speed ramp" in {
      val points = (0 until 9).map(index => pointAt(index, vertical = 10.0 + 5.0 * index))

      val results = compute(points, defaultConfig)
      val metric  = results(4)

      metric.acceleration shouldBe GeoVector(north = 0.0, east = 0.0, vertical = 10.0)
      metric.accelerationMagnitude shouldBe 10.0 +- 1e-9
      metric.totalSpeed shouldBe 30.0 +- 1e-9
      metric.energyRate shouldBe (30.0 * 10.0 - 9.80665 * 30.0) +- 1e-9
      metric.diveAngle shouldBe 90.0 +- 1e-9
      metric.glideRatio shouldBe 0.0 +- 1e-9
    }

    "compute along-track and cross-track acceleration" in {
      val points = (0 until 9).map(index => pointAt(index, north = 30.0, east = index.toDouble))

      val results = compute(points, defaultConfig)
      val metric  = results(4)

      metric.acceleration shouldBe GeoVector(north = 0.0, east = 2.0, vertical = 0.0)
      metric.accelerationAlongTrack shouldBe (8.0 / Math.sqrt(916.0)) +- 1e-9
      metric.accelerationCrossTrack shouldBe (60.0 / Math.sqrt(916.0)) +- 1e-9
    }

    "unwrap the course when it crosses north" in {
      val courses = List(350.0, 355.0, 0.0, 5.0, 10.0)
      val points  = courses.zipWithIndex.map {
        case (course, index) =>
          val radians = Math.toRadians(course)
          pointAt(index, north = 30.0 * Math.cos(radians), east = 30.0 * Math.sin(radians))
      }

      val results = compute(points, defaultConfig)
      val metric  = results(2)

      metric.course shouldBe 0.0 +- 1e-6
      metric.courseRate shouldBe 10.0 +- 1e-6
      metric.turnRate shouldBe 10.0 +- 1e-6
    }

    "clamp the window at the stream edges" in {
      val points = (0 until 3).map(index => pointAt(index, vertical = 10.0 + 10.0 * index))

      val results = compute(points, defaultConfig)

      results.size shouldBe 3
      results.foreach(_.acceleration.vertical shouldBe 20.0 +- 1e-9)
    }

    "use single-point windows with a zero half-window" in {
      val points = (0 until 3).map(index => pointAt(index, vertical = 10.0 + 10.0 * index))

      val results = compute(points, defaultConfig.copy(slopeHalfWindow = 0))

      results.foreach(_.acceleration shouldBe GeoVector.zero)
      results.foreach(_.turnRate shouldBe 0.0)
    }

    "fill the GPS metrics from the GpsReader" in {
      val points = (0 until 9).map(index => pointAt(index, north = 30.0, vertical = 10.0, gps = Some(GpsFix)))

      val results = compute(points, defaultConfig)
      val metric  = results(4)

      metric.horizontalAccuracy shouldBe Some(1.0)
      metric.verticalAccuracy shouldBe Some(2.0)
      metric.speedAccuracy shouldBe Some(0.5)
      metric.courseAccuracy shouldBe Some(3.0)
      metric.courseAccuracyEstimate.get shouldBe Math.toDegrees(0.5 / Math.sqrt(1000.0)) +- 1e-9
      metric.satellites shouldBe Some(11)
      metric.sphericalErrorProbably.get shouldBe 0.5127 * 4.0 +- 1e-9
      metric.speedScoreAccuracy.get shouldBe Math.sqrt(2.0) * 2.0 / 3.0 +- 1e-9
    }

    "leave the GPS metrics empty when the format has no data" in {
      val points = (0 until 9).map(index => pointAt(index, north = 30.0, vertical = 10.0))

      val results = compute(points, defaultConfig)
      val metric  = results(4)

      metric.horizontalAccuracy shouldBe None
      metric.verticalAccuracy shouldBe None
      metric.speedAccuracy shouldBe None
      metric.courseAccuracy shouldBe None
      metric.courseAccuracyEstimate shouldBe None
      metric.satellites shouldBe None
      metric.sphericalErrorProbably shouldBe None
      metric.speedScoreAccuracy shouldBe None
    }

    "compute every metric of a stationary flight" in {
      val points = (0 until 9).map(index => pointAt(index, gps = Some(GpsFix)))

      val results = compute(points, defaultConfig)

      val expected = FlightMetric(
        time = Instant.ofEpochMilli(0),
        altitude = 1000.0,
        verticalSpeed = 0.0,
        horizontalSpeed = 0.0,
        totalSpeed = 0.0,
        course = 0.0,
        courseRate = 0.0,
        courseAccuracy = Some(3.0),
        courseAccuracyEstimate = Some(0.0),
        glideRatio = 0.0,
        liftCoefficient = 0.0,
        dragCoefficient = 0.0,
        diveAngle = 0.0,
        diveRate = 0.0,
        turnRate = 0.0,
        speedAccuracy = Some(0.5),
        horizontalAccuracy = Some(1.0),
        verticalAccuracy = Some(2.0),
        satellites = Some(11),
        acceleration = GeoVector.zero,
        accelerationMagnitude = 0.0,
        accelerationAlongTrack = 0.0,
        accelerationCrossTrack = 0.0,
        totalEnergy = 9.80665 * 1000.0,
        energyRate = 0.0,
        sphericalErrorProbably = Some(0.5127 * 4.0),
        speedScoreAccuracy = Some(Math.sqrt(2.0) * 2.0 / 3.0),
      )

      results.head shouldBe expected
    }

    "fall back to the derived estimate when the format provides no course accuracy" in {
      val points = (0 until 9).map(index =>
        pointAt(index, north = 30.0, vertical = 40.0, gps = Some(Gps(1.0, 2.0, 0.5, None, 11)))
      )

      val results = compute(points, defaultConfig)
      val metric  = results(4)

      val expectedEstimate = Math.toDegrees(0.5 / 50.0)

      metric.courseAccuracy shouldBe Some(expectedEstimate)
      metric.courseAccuracyEstimate shouldBe Some(expectedEstimate)
      metric.speedAccuracy shouldBe Some(0.5)
      metric.satellites shouldBe Some(11)
    }

  }

  private def compute(points: Seq[MetricPoint], config: MetricsConfig): List[FlightMetric] =
    fs2
      .Stream
      .emits(points)
      .through(FlightMetricsCalculator(config).compute[IO, MetricPoint])
      .streamResult()

  private def pointAt(
    index: Int,
    north: Double = 0.0,
    east: Double = 0.0,
    vertical: Double = 0.0,
    altitude: Double = 1000.0,
    gps: Option[Gps] = None,
  ): MetricPoint =
    MetricPoint(
      time = Instant.ofEpochMilli(index * 500),
      altitude = altitude,
      speed = GeoVector(north = north, east = east, vertical = vertical),
      gps = gps,
    )

}

final private case class Gps(
  horizontalAccuracy: Double,
  verticalAccuracy: Double,
  speedAccuracy: Double,
  courseAccuracy: Option[Double],
  satellites: Int,
)

final private case class MetricPoint(
  time: Instant,
  altitude: Double,
  speed: GeoVector,
  gps: Option[Gps],
)

private object MetricPoint {

  given PointReader[MetricPoint] with {
    def time(point: MetricPoint): Instant    = point.time
    def altitude(point: MetricPoint): Double = point.altitude
    def speed(point: MetricPoint): GeoVector = point.speed
  }

  given GpsReader[MetricPoint] with {
    def horizontalAccuracy(point: MetricPoint): Option[Double] =
      point.gps.map(_.horizontalAccuracy)
    def verticalAccuracy(point: MetricPoint): Option[Double] =
      point.gps.map(_.verticalAccuracy)
    def speedAccuracy(point: MetricPoint): Option[Double] =
      point.gps.map(_.speedAccuracy)
    def courseAccuracy(point: MetricPoint): Option[Double] =
      point.gps.flatMap(_.courseAccuracy)
    def satellites(point: MetricPoint): Option[Int] =
      point.gps.map(_.satellites)
  }

}
