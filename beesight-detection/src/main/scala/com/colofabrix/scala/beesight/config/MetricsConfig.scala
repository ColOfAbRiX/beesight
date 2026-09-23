package com.colofabrix.scala.beesight.config

/**
 * Configuration for the per-point flight metrics computation.
 *
 * @param slopeHalfWindow Half-size of the least-squares window in points; the full window is `2 * slopeHalfWindow + 1`
 *                        samples, replicating FlySight's `getSlope` (±4 points by default)
 * @param mass            Total flying weight of pilot and equipment (kg), used by the lift/drag coefficients
 * @param planformArea    Canopy planform area (m²), used by the lift/drag coefficients
 */
final case class MetricsConfig(
  slopeHalfWindow: Int,
  mass: Double,
  planformArea: Double,
)
