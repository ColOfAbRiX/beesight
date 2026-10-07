package com.colofabrix.scala.beesight.detection.model

/**
 * The four flight stage transition events detected by the algorithm.
 *
 *   - `Takeoff` — transition from ground to climbing in the aircraft
 *   - `Freefall` — transition from climbing to freefall after exit
 *   - `Canopy` — transition from freefall to descending under canopy
 *   - `Landing` — transition from canopy flight to being on the ground
 */
private[beesight] enum EventType {
  case Takeoff, Freefall, Canopy, Landing
}
