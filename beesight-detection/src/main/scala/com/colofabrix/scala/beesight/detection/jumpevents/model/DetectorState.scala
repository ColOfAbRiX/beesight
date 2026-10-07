package com.colofabrix.scala.beesight.detection.jumpevents.model

import cats.data.IndexedState
import com.colofabrix.scala.beesight.detection.collections.NonEmptyWindow.*
import com.colofabrix.scala.beesight.detection.model.*

private[jumpevents] type Transition[S <: DetectorState, A] =
  IndexedState[S, DetectorState, A]

private[jumpevents] type Step[S <: DetectorState, A] =
  IndexedState[S, S, A]

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
 * A single data point enriched with the kinematics used by the stage handlers, the current phase, and the
 * accumulated detection events.
 */
final private[jumpevents] case class WindowEntry(
  point: IndexedPoint,
  smoothedSpeed: GeoVector,
  acceleration: GeoVector,
  phase: FlightPhase,
  detectedEvents: FlightEvents,
)

/**
 * Controls whether the focus starts at position zero or at the event-specific backtrack offset.
 */
private[jumpevents] enum FocusPosition {
  case Zero, StageFocus
}
