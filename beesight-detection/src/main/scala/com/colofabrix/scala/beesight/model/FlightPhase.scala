package com.colofabrix.scala.beesight.model

/**
 * Represents the sequential phases of a skydiving jump.
 *
 *   - `BeforeTakeoff` (0) — on the ground or preparing for the jump
 *   - `Climbing` (1) — in the aircraft, gaining altitude
 *   - `Freefall` (2) — after exit, in free fall
 *   - `UnderCanopy` (3) — after deployment, descending under canopy
 *   - `Landed` (4) — on the ground after landing
 */
private[beesight] enum FlightPhase(val sequence: Int) {
  case BeforeTakeoff extends FlightPhase(0)
  case Climbing      extends FlightPhase(1)
  case Freefall      extends FlightPhase(2)
  case UnderCanopy   extends FlightPhase(3)
  case Landed        extends FlightPhase(4)
}
