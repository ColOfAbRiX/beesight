package com.colofabrix.scala.beesight.detection.model

enum StreamPhase {
  case Streaming
  case Validation(remainingPoints: Int, eventType: EventType)
}
