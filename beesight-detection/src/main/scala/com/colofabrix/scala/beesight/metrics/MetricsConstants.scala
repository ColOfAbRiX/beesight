package com.colofabrix.scala.beesight.metrics

/**
 *  Physical constants used by the flight metrics computations.
 */
private[metrics] object MetricsConstants {

  /** Standard gravity (m/s²). */
  val Gravity: Double = 9.80665

  /** ISA standard air density at sea level (kg/m³). */
  val SeaLevelAirDensity: Double = 1.225

  /** ISA troposphere density lapse coefficient (1/m). */
  val IsaLapseCoefficient: Double = 2.25577e-5

  /** ISA troposphere density lapse exponent (dimensionless). */
  val IsaDensityExponent: Double = 4.25588

}
