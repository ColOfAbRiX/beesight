package com.colofabrix.scala.beesight.detection.model

final case class PointKinematics(
  rawVerticalSpeed: Double,
  rawNorthSpeed: Double,
  rawEastSpeed: Double,
  clippedVerticalSpeed: Double,
  clippedNorthSpeed: Double,
  clippedEastSpeed: Double,
  correctedAltitude: Double,
  horizontalSpeed: Double,
  totalSpeed: Double,
  deltaTime: Double,
)
