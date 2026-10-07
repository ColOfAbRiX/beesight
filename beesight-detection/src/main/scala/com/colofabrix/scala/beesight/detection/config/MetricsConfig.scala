package com.colofabrix.scala.beesight.detection.config

/**
 * Configuration for the per-point flight metrics computation.
 *
 * TODO: Is `slopeHalfWindow` the best parameter to make confifugranble? It seems difficult to understand for a human. Discuss.
 * @param slopeHalfWindow Half-size of the least-squares window in points; the full window is `2 * slopeHalfWindow + 1`
 * @param mass            Total flying weight of pilot and equipment (kg), used by the lift/drag coefficients
 * @param planformArea    Canopy planform area (m²), used by the lift/drag coefficients
 * @param groundElevation Optional ground elevation in m (MSL).
 */
final case class MetricsConfig(
  slopeHalfWindow: Int,
  mass: Mass,
  planformArea: Area,
  groundElevation: Option[Altitude] = None,
)
