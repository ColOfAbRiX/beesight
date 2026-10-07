package com.colofabrix.scala.beesight.detection.metrics

/**
 *  Physical constants used by the flight metrics computations.
 */
private[metrics] object MetricsConstants {

  /** Standard gravity (m/s²). */
  val Gravity: Double = 9.80665

  /** ISA standard sea-level atmosphere pressure (Pa). */
  val SeaLevelPressure: Double = 101325.0

  /** ISA temperature lapse rate in the troposphere (K/m). */
  val TemperatureLapseRate: Double = 0.0065

  /** ISA standard sea-level temperature (K). */
  val SeaLevelTemperature: Double = 288.15

  /** Molar mass of dry air (kg/mol). */
  val MolarMassDryAir: Double = 0.0289644

  /** Universal gas constant (J/mol/K). */
  val UniversalGasConstant: Double = 8.31447

}
