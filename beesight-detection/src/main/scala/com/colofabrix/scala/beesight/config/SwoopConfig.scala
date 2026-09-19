package com.colofabrix.scala.beesight.config

/**
 * Configuration for swoop metrics computation.
 *
 * @param minSwoopVerticalSpeed Minimum peak vertical speed (m/s) required to validate a swoop; below this, no swoop is reported
 * @param minHorizontalSpeed Minimum horizontal speed (m/s) below which heading is considered too noisy for rotation computation
 * @param maxSwoopHeight Maximum AGL height (m) at which a dive point can be detected; bounds the candidate peak search
 * @param recoveryFraction Fraction of the peak vertical speed above which a sustained re-ascent marks a manoeuvre recovery, disqualifying the candidate as dive point
 * @param minSwoopDescent Minimum descent (m) from the dive point to the ground; rejects flare-level speed bumps near touchdown
 * @param pullThreshold Descent rate (m/s) below which the dive is considered over
 * @param rolloutWindow Height above the stop altitude (m) where the rollout search starts
 * @param sustainedRecoverySamples Consecutive samples above the recovery level that mark a manoeuvre recovery, disqualifying the candidate as dive point
 * @param recoveryHeight Minimum AGL height (m) at which a sustained recovery disqualifies the candidate; recoveries closer to the ground are canopy cycles after a swoop that ended high, not manoeuvres
 */
final case class SwoopConfig(
  minSwoopVerticalSpeed: Double,
  minHorizontalSpeed: Double,
  maxSwoopHeight: Double,
  recoveryFraction: Double,
  minSwoopDescent: Double,
  pullThreshold: Double,
  rolloutWindow: Double,
  sustainedRecoverySamples: Int,
  recoveryHeight: Double,
)
