package com.colofabrix.scala.beesight.detection.model

import cats.data.State
import cats.syntax.all.*
import com.colofabrix.scala.beesight.collections.NonEmptyWindow
import com.colofabrix.scala.beesight.collections.NonEmptyWindow
import com.colofabrix.scala.beesight.model.*

final private[detection] case class DetectionData(
  point: DataPoint,
  kinematics: PointKinematics,
  phase: Option[FlightPhase],
  detectedEvents: Option[FlightEvents],
)

private[detection] enum DetectionState {

  case Filling(
    window: NonEmptyWindow[DetectionData],
  ) extends DetectionState

  case Running(
    window: NonEmptyWindow.NonEmptyFilled[DetectionData],
    phase: FlightPhase,
    focus: Int,
  ) extends DetectionState

}

object DetectionState {

  def fillingModify(f: DetectionState.Filling => DetectionState): State[DetectionState, Unit] =
    State.modify {
      case state: DetectionState.Filling => f(state)
      case state: DetectionState.Running => state
    }

  def runningState[A](f: DetectionState.Running => (DetectionState, A)): State[DetectionState, Option[A]] =
    State.apply {
      case state: DetectionState.Filling => (state, None)
      case state: DetectionState.Running => f(state).bimap(identity, Some(_))
    }

}
