package com.colofabrix.scala.beesight.model

/**
 * Represents the phases of a skydiving jump
 */
enum FlightPhase(val sequence: Int) {
  case BeforeTakeoff extends FlightPhase(0)
  case Takeoff       extends FlightPhase(1)
  case Freefall      extends FlightPhase(2)
  case Canopy        extends FlightPhase(3)
  case Landing       extends FlightPhase(4)
}
