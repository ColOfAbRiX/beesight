package com.colofabrix.scala.beesight.detection.model

import com.colofabrix.scala.beesight.model.FlightPhase

enum StreamStatePhase {
  case Streaming
  case WaitingValidation(remainingPoints: Int, eventType: FlightPhase)
}
