package com.colofabrix.scala.beesight.detection.model

import cats.data.IndexedState
import com.colofabrix.scala.beesight.collections.NonEmptyWindow.*
import com.colofabrix.scala.beesight.model.*

/**
 * Common supertype for detection pipeline states.
 */
sealed private[detection] trait StateTrait

/**
 * State while the sliding window is still accumulating initial points.
 */
final private[detection] case class FillingState(
  window: NonEmptyFilling[DetectionData],
  phase: FlightPhase,
  focusType: FocusPosition,
) extends StateTrait

private[detection] object FillingState {

  extension (self: FillingState)
    def toDetectionState =
      DetectionState(self.window.toNonEmptyFilled, self.phase, self.focusType)

}

/**
 * State when the sliding window is full and actively detecting flight stages.
 */
final private[detection] case class DetectionState(
  window: NonEmptyFilled[DetectionData],
  phase: FlightPhase,
  focusType: FocusPosition,
) extends StateTrait

/**
 * A single data point enriched with kinematics, current phase, and accumulated detection events.
 */
final private[detection] case class DetectionData(
  point: IndexedDataPoint,
  kinematics: PointKinematics,
  phase: FlightPhase,
  detectedEvents: FlightEvents,
)

/**
 * Controls whether the focus starts at position zero or at the stage-specific backtrack offset.
 */
private[detection] enum FocusPosition {
  case Zero, StageFocus
}

private[detection] type Transition[S <: StateTrait, A] =
  IndexedState[S, StateTrait, A]

private[detection] type KeepState[S <: StateTrait, A] =
  IndexedState[S, S, A]
