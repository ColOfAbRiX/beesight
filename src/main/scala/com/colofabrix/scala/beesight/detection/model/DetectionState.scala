package com.colofabrix.scala.beesight.detection.model

import cats.data.State
import cats.syntax.all.*
import com.colofabrix.scala.beesight.collections.NonEmptyWindow.*
import com.colofabrix.scala.beesight.model.*

sealed trait StateTrait

final case class FillingState(
  window: NonEmptyFilling[DetectionData],
) extends StateTrait

final case class DetectionState(
  window: NonEmptyFilled[DetectionData],
  phase: FlightPhase,
) extends StateTrait

final case class DetectionData(
  point: DataPoint,
  kinematics: PointKinematics,
  phase: Option[FlightPhase],
  detectedEvents: Option[FlightEvents],
)
