package com.colofabrix.scala.beesight.metadata

import io.circe.{ Decoder, Encoder }
import java.time.Instant

/** The detected flight stage points flattened for JSON storage. */
final case class StoredFlightEvents(
  takeoff: Option[StoredPoint],
  freefall: Option[StoredPoint],
  canopy: Option[StoredPoint],
  landing: Option[StoredPoint],
) derives Encoder.AsObject, Decoder

/** Swoop metrics flattened for JSON storage. */
final case class StoredSwoopData(
  turnInitiation: StoredPoint,
  rolloutStart: StoredPoint,
  maxVerticalSpeed: StoredSpeedAtAltitude,
  maxTotalSpeed: StoredSpeedAtAltitude,
  maxHorizontalSpeed: StoredSpeedAtAltitude,
  degreesOfRotation: Double,
  turnDirection: String,
  timeToExecuteTurnMillis: Long,
  ground: Double,
  patternEntry: StoredPoint,
  divePoint: StoredPoint,
  stop: StoredPoint,
  bottomOfTurn: StoredPoint,
) derives Encoder.AsObject, Decoder

/** Summary of a cut operation applied to the input file. */
final case class StoredCutSummary(
  applied: Boolean,
  totalPoints: Long,
  retainedPoints: Long,
  firstRetainedIndex: Long,
  lastRetainedIndex: Long,
) derives Encoder.AsObject, Decoder

/** Per-point flight metrics flattened for JSON storage. */
final case class StoredMetric(
  time: Instant,
  altitude: Double,
  verticalSpeed: Double,
  horizontalSpeed: Double,
  totalSpeed: Double,
  course: Double,
  courseRate: Double,
  courseAccuracy: Option[Double],
  courseAccuracyEstimate: Option[Double],
  glideRatio: Double,
  liftCoefficient: Double,
  dragCoefficient: Double,
  diveAngle: Double,
  diveRate: Double,
  turnRate: Double,
  speedAccuracy: Option[Double],
  horizontalAccuracy: Option[Double],
  verticalAccuracy: Option[Double],
  satellites: Option[Int],
  accelerationNorth: Double,
  accelerationEast: Double,
  accelerationVertical: Double,
  accelerationMagnitude: Double,
  accelerationAlongTrack: Double,
  accelerationCrossTrack: Double,
  totalEnergy: Double,
  energyRate: Double,
  sphericalErrorProbably: Option[Double],
  speedScoreAccuracy: Option[Double],
) derives Encoder.AsObject, Decoder

/** A flight point flattened for JSON storage. */
final case class StoredPoint(
  index: Long,
  time: Instant,
  altitude: Double,
  speedNorth: Double,
  speedEast: Double,
  speedVertical: Double,
) derives Encoder.AsObject, Decoder

/** A speed magnitude at a given altitude and stream position, flattened for JSON storage. */
final case class StoredSpeedAtAltitude(
  idx: Long,
  speed: Double,
  altitude: Double,
) derives Encoder.AsObject, Decoder
