package com.colofabrix.scala.beesight.detection.model

import cats.kernel.Monoid
import cats.syntax.semigroup.*
import com.colofabrix.scala.beesight.model.*

/**
 * Result of phase detection for a single point.
 */
private[detection] final case class DetectionResult(
  currentPhase: FlightPhase,
  events: FlightEvents,
  missedTakeoff: Boolean,
)

object DetectionResult {

  given Monoid[DetectionResult] with {
    def empty: DetectionResult =
      DetectionResult(FlightPhase.BeforeTakeoff, Monoid[FlightEvents].empty, false)

    def combine(x: DetectionResult, y: DetectionResult): DetectionResult =
      val (first, second) = if x.events.lastPoint <= y.events.lastPoint then (x, y) else (y, x)
      val mergedEvents    = first.events |+| second.events
      val missedNow       = mergedEvents.freefall.isDefined && mergedEvents.takeoff.isEmpty

      DetectionResult(
        currentPhase = if x.currentPhase.sequence > y.currentPhase.sequence then x.currentPhase else y.currentPhase,
        events = mergedEvents,
        missedTakeoff = x.missedTakeoff || y.missedTakeoff || missedNow,
      )
  }

  given Monoid[FlightEvents] with {
    def empty: FlightEvents =
      FlightEvents(None, None, None, None, 0, true)

    def combine(x: FlightEvents, y: FlightEvents): FlightEvents =
      val (first, second) = if x.lastPoint < y.lastPoint then (x, y) else (y, x)
      FlightEvents(
        takeoff = first.takeoff orElse second.takeoff,
        freefall = first.freefall orElse second.freefall,
        canopy = first.canopy orElse second.canopy,
        landing = first.landing orElse second.landing,
        lastPoint = second.lastPoint,
        isValid = first.isValid && second.isValid,
      )
  }

}
