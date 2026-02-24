package com.colofabrix.scala.beesight.detection.model

import cats.data.IndexedState
import cats.syntax.all.*
import com.colofabrix.scala.beesight.collections.NonEmptyWindow.*
import com.colofabrix.scala.beesight.model.*

sealed trait StateTrait

final case class FillingState(
  window: NonEmptyFilling[DetectionData],
  phase: FlightPhase,
  focusIdx: Int
) extends StateTrait

final case class DetectionState(
  window: NonEmptyFilled[DetectionData],
  phase: FlightPhase,
  focusIdx: Int
) extends StateTrait

final case class DetectionData(
  point: IndexedDataPoint,
  kinematics: PointKinematics,
  phase: FlightPhase,
  detectedEvents: FlightEvents,
)

type Transition[S <: StateTrait, A] =
  IndexedState[S, StateTrait, A]

type KeepState[S <: StateTrait, A] =
  IndexedState[S, S, A]
