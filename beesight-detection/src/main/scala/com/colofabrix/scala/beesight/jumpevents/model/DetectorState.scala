package com.colofabrix.scala.beesight.jumpevents.model

import cats.data.IndexedState
import com.colofabrix.scala.beesight.collections.NonEmptyWindow.*
import com.colofabrix.scala.beesight.model.*

/**
 * Common supertype for detection pipeline states.
 */
sealed private[jumpevents] trait DetectorState

/**
 * State while the sliding window is still accumulating initial points.
 */
final private[jumpevents] case class FillingState(
  window: NonEmptyFilling[WindowEntry],
  phase: FlightPhase,
  focusType: FocusPosition,
) extends DetectorState

private[jumpevents] object FillingState {

  extension (self: FillingState) {
    def toDetecting: DetectingState =
      DetectingState(self.window.toNonEmptyFilled, self.phase, self.focusType)
  }

}

/**
 * State when the sliding window is full and actively detecting flight event points.
 */
final private[jumpevents] case class DetectingState(
  window: NonEmptyFilled[WindowEntry],
  phase: FlightPhase,
  focusType: FocusPosition,
) extends DetectorState

/**
 * A single data point enriched with kinematics, current phase, and accumulated detection events.
 *
 * FIXME: There's confusion in this data because there are repetitions. `kinematics` and `point` overlanp,
 *        `kinematics.acceleration` is computed with non-smoothed data and we should highlight that
 */
final private[jumpevents] case class WindowEntry(
  point: IndexedPoint,
  kinematics: PointKinematics,
  phase: FlightPhase,
  detectedEvents: FlightEvents,
)

/**
 * Controls whether the focus starts at position zero or at the event-specific backtrack offset.
 */
private[jumpevents] enum FocusPosition {
  case Zero, StageFocus
}

private[jumpevents] type Transition[S <: DetectorState, A] =
  IndexedState[S, DetectorState, A]

private[jumpevents] type Step[S <: DetectorState, A] =
  IndexedState[S, S, A]
