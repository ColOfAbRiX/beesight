package com.colofabrix.scala.beesight.metrics

import com.colofabrix.scala.beesight.model.GeoVector
import java.time.Instant

final case class FlightMetric(
  time: Instant,
  altitude: Double,
  // Speed
  verticalSpeed: Double,
  horizontalSpeed: Double,
  totalSpeed: Double,
  // Not yet sure how "course" works
  course: Double,
  courseRate: Double,
  courseAccuracy: Option[Double],
  // Flight performance
  glideRatio: Double,
  liftCoefficient: Double,
  dragCoefficient: Double,
  // Dive
  diveAngle: Double,
  diveRate: Double,
  // Accuracy
  speedAccuracy: Option[Double],
  horizontalAccuracy: Option[Double],
  verticalAccuracy: Option[Double],
  satellites: Option[Int],
  // Acceleration
  acceleration: GeoVector,
  accelerationMagnitude: Double,
  accelerationAlongTrack: Double,
  accelerationCrossTrack: Double,
  // Energy
  totalEnergy: Double,
  energyRate: Double,
  // Other
  shericalErrorProbably: Option[Double],
  speedScoreAccuracy: Option[Double],
)
