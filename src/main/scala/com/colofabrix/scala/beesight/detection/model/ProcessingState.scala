package com.colofabrix.scala.beesight.detection.model

import com.colofabrix.scala.beesight.model.{ InputFlightRow, OutputFlightRow }

final case class ProcessingResult[A](
  nextState: ProcessingState[A],
  outputs: Vector[OutputFlightRow[A]],
)

final case class ProcessingState[A](
  index: Long,
  currentPoint: InputFlightRow[A],
  previousKinematics: Option[PointKinematics],
  streamPhase: StreamPhase,
  detectedEvents: DetectedEvents,
  pendingBuffer: Vector[ProcessingState[A]],
  takeoffState: EventState,
  freefallState: EventState,
  canopyState: EventState,
  landingState: EventState,
)

object ProcessingState {

  extension [A](self: ProcessingState[A]) {

    def addToBuffer(): Vector[ProcessingState[A]] =
      self.pendingBuffer :+ self

    def toOutputRow: OutputFlightRow[A] =
      OutputFlightRow(
        takeoff = self.detectedEvents.takeoff,
        freefall = self.detectedEvents.freefall,
        canopy = self.detectedEvents.canopy,
        landing = self.detectedEvents.landing,
        source = self.currentPoint.source,
      )

    def getEventState(eventType: EventType): EventState =
      eventType match {
        case EventType.Takeoff  => self.takeoffState
        case EventType.Freefall => self.freefallState
        case EventType.Canopy   => self.canopyState
        case EventType.Landing  => self.landingState
      }

  }

}
