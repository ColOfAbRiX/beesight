package com.colofabrix.scala.beesight.detection.model

import cats.data.IndexedState
import com.colofabrix.scala.beesight.collections.NonEmptyWindow.*
import com.colofabrix.scala.beesight.model.*

/**
 * Common supertype for detection pipeline states.
 */
sealed private[detection] trait DetectorState

/**
 * State while the sliding window is still accumulating initial points.
 */
final private[detection] case class FillingState(
  window: NonEmptyFilling[WindowEntry],
  phase: FlightPhase,
  focusType: FocusPosition,
) extends DetectorState

private[detection] object FillingState {

  extension (self: FillingState) {
    def toDetecting: DetectingState =
      DetectingState(self.window.toNonEmptyFilled, self.phase, self.focusType)
  }

}

/**
 * State when the sliding window is full and actively detecting flight stages.
 */
final private[detection] case class DetectingState(
  window: NonEmptyFilled[WindowEntry],
  phase: FlightPhase,
  focusType: FocusPosition,
) extends DetectorState

/**
 * A single data point enriched with kinematics, current phase, and accumulated detection events.
 */
final private[detection] case class WindowEntry(
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

private[detection] type Transition[S <: DetectorState, A] =
  IndexedState[S, DetectorState, A]

private[detection] type Step[S <: DetectorState, A] =
  IndexedState[S, S, A]
