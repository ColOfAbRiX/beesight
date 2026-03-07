package com.colofabrix.scala.beesight.detection.model

import cats.data.IndexedState
import cats.syntax.all.*
import com.colofabrix.scala.beesight.collections.NonEmptyWindow.*
import com.colofabrix.scala.beesight.model.*

sealed trait StateTrait

final case class FillingState(
  window: NonEmptyFilling[DetectionData],
  phase: FlightPhase,
  focusType: FocusPosition,
) extends StateTrait

object FillingState {

  extension (self: FillingState)
    def toDetectionState =
      DetectionState(self.window.toNonEmptyFilled, self.phase, self.focusType)

}

final case class DetectionState(
  window: NonEmptyFilled[DetectionData],
  phase: FlightPhase,
  focusType: FocusPosition,
) extends StateTrait

final case class DetectionData(
  point: IndexedDataPoint,
  kinematics: PointKinematics,
  phase: FlightPhase,
  detectedEvents: FlightEvents,
)

enum FocusPosition {
  case Zero, StageFocus
}

type Transition[S <: StateTrait, A] =
  IndexedState[S, StateTrait, A]

type KeepState[S <: StateTrait, A] =
  IndexedState[S, S, A]
