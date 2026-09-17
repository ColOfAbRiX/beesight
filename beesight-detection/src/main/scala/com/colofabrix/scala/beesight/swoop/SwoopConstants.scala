package com.colofabrix.scala.beesight.swoop

/**
 * Detection thresholds for the swoop pipeline.
 */
private[swoop] object SwoopConstants {

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

  /** Unwrap pass: descent rate above which a point is inside a fast dive. */
  val diveVerticalSpeed = 26.8224 // 60mph

  //  Angles  //

  /** Stop: heading deviation from the swoop heading that ends the search. */
  val stopHeadingDeviation = 45.0

  /** Steepening walk: turn rate that aborts the walk. */
  val maxTurnRate = 30.0

  /** Unwrap pass: dive angle above which a point is inside a steep dive. */
  val diveAngleCap = 60.0

  /** Unwrap pass: per-sample turn angle above which a point turns right, below which it turns left. */
  val turnStateThreshold = 2.0

  /** Unwrap pass: resolved segment heading change below which the segment angles are replaced. */
  val segmentAngleCap = 315.0

  /** Unwrap pass: maximum dead-reckoned displacement of a segment from its start. */
  val segmentDisplacementCap = 9.144 // 30ft

  /** Turn degrees: per-sample turn angles at or above this value are skipped. */
  val fullTurnAngle = 360.0

}
