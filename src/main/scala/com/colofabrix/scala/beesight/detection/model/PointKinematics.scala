package com.colofabrix.scala.beesight.detection.model

import java.time.Instant

final case class PointKinematics(
  time: Instant,
  altitude: Double,
  speed: GeoVector,
  acceleration: GeoVector
)
