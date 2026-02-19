package com.colofabrix.scala.beesight.model

/**
 * Represents the phases of a skydiving jump
 */
enum FlightPhase(val sequence: Int) {
  case BeforeTakeoff extends FlightPhase(0)
  case Climbing      extends FlightPhase(1)
  case Freefall      extends FlightPhase(2)
  case UnderCanopy   extends FlightPhase(3)
  case Landed        extends FlightPhase(4)
}

object FlightPhase {

  def fromEvent(event: EventType): FlightPhase =
    event match {
      case EventType.Takeoff  => FlightPhase.Climbing
      case EventType.Freefall => FlightPhase.Freefall
      case EventType.Canopy   => FlightPhase.UnderCanopy
      case EventType.Landing  => FlightPhase.Landed
    }

}
