package com.colofabrix.scala.beesight.detection.model

import com.colofabrix.scala.beesight.model.{ InputFlightRow, OutputFlightRow }

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

final case class ProcessingResult[A](
  nextState: ProcessingState[A],
  outputs: Vector[OutputFlightRow[A]],
)
