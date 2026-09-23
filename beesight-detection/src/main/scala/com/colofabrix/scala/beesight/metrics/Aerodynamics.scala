package com.colofabrix.scala.beesight.metrics

import com.colofabrix.scala.beesight.config.MetricsConfig
import com.colofabrix.scala.beesight.metrics.MetricsConstants.*
import java.lang.Math.*
import scala.math.pow

/**
 * Aerodynamic coefficients from quasi-steady glide equilibrium, replicating FlySight's `initAerodynamics`.
 *
 * In steady flight the weight resolves into `L = W·cos(γ)` perpendicular to the flight path and `D = W·sin(γ)` along
 * it, giving `C = 2·m·g·f(γ) / (ρ·V²·S)` with γ the dive angle, V the total speed, ρ the ISA air density and S the
 * planform area.
 */
final private[metrics] class Aerodynamics private (config: MetricsConfig) {

  /** Lift coefficient `2·m·g·cos(γ) / (ρ·V²·S)`; 0 when the total speed is zero. */
  def liftCoefficient(diveAngle: Double, totalSpeed: Double, altitude: Double): Double =
    coefficient(diveAngle, totalSpeed, altitude)(cos)

  /** Drag coefficient `2·m·g·sin(γ) / (ρ·V²·S)`; 0 when the total speed is zero. */
  def dragCoefficient(diveAngle: Double, totalSpeed: Double, altitude: Double): Double =
    coefficient(diveAngle, totalSpeed, altitude)(sin)

  private def coefficient(
    diveAngle: Double,
    totalSpeed: Double,
    altitude: Double
  )(weightComponent: Double => Double,
  ): Double =
    if totalSpeed == 0.0 then
      0.0
    else
      val dynamicPressure = airDensity(altitude) * totalSpeed * totalSpeed * config.planformArea
      2.0 * config.mass * Gravity * weightComponent(toRadians(diveAngle)) / dynamicPressure

  /** ISA troposphere air density (kg/m³) at the given altitude (m). */
  private def airDensity(altitude: Double): Double =
    SeaLevelAirDensity * pow(1.0 - IsaLapseCoefficient * altitude, IsaDensityExponent)

}

object Aerodynamics {

  def apply(config: MetricsConfig): Aerodynamics =
    new Aerodynamics(config)

}
