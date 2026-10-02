package com.colofabrix.scala.beesight.swoop

/**
 * Detection thresholds for the swoop pipeline.
 */
private[swoop] object SwoopConstants {

  // FIXME: All of these should be configurable values in reference.conf

  //  Altitudes  //

  /** Pattern entry: first crossing below this AGL altitude. */
  val patternEntryAltitude = 762.0 // 2500ft

  /** Dive point: maximum AGL altitude at which the maximum-descent scan applies. */
  val diveAltitudeCap = 304.8 // 1000ft

  /** Steepening walk: maximum altitude gain before the walk is aborted. */
  val maxAltitudeGain = 152.4 // 500ft

  /** Bottom of turn: altitude offset above the bottom altitude for the backward bracket. */
  val bottomAltitudeOffset = 1.0

  //  Speeds  //

  /** Steepening walk: descent rate above which the accumulator is armed. */
  val steepeningEntry = 6.7056 // 15mph

  /** Steepening walk: accumulated descent-rate increase that ends the walk. */
  val steepeningAccumulator = 6.7056 // 15mph

  /** Stop search: descent rate below which a point can fix the swoop heading. */
  val stopDescentThreshold = 4.4704 // 10mph

  /** Stop search: horizontal speed below which a point can fix the swoop heading. */
  val stopHorizontalThreshold = 8.9408 // 20mph

  /** Stop: horizontal speed below which the stop is confirmed. */
  val stopEndHorizontal = 0.89408 // 2mph

  /** Bottom candidate: descent rate above which the forward walk continues. */
  val bottomDescentThreshold = 4.4704 // 10mph

  /** Bottom bracket: descent rate whose forward crossing brackets the bottom of turn. */
  val bottomVzCross = 1.25

  /** Ground fallback: total speed below which a point counts as slow. */
  val groundSlowSpeed = 4.4704 // 10mph

  //  Angles  //

  /** Stop: heading deviation from the swoop heading that ends the search. */
  val stopHeadingDeviation = 45.0

  /** Steepening walk: turn rate that aborts the walk. */
  val maxTurnRate = 30.0

  //  Carve (turn angle)  //

  /** Carve: smoothed turn rate above which a sample belongs to the carve. */
  val carveRateThreshold = 20.0 // deg/s

  /** Carve: minimum horizontal speed for the GPS heading to be reliable. */
  val carveReliableSpeed = 10.0 // ~22mph

  /** Carve: maximum run of below-threshold samples tolerated inside the carve. */
  val carveGapSamples = 5

  /** Carve: samples either side of the dive point establishing the turn direction. */
  val carveDirectionSamples = 10

  /** Carve: minimum net heading change around the dive point for a turn direction to be established. */
  val carveMinDirectionChange = 5.0 // deg

}
