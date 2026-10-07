package com.colofabrix.scala.beesight.detection.metrics.computations

import com.colofabrix.scala.beesight.detection.config.MetricsConfig
import com.colofabrix.scala.beesight.detection.metrics.MetricsConstants.*
import com.colofabrix.scala.beesight.detection.model.GeoVector
import java.lang.Math.*

/**
 * Aerodynamic coefficients from the gravity-corrected acceleration projected on the flight path
 *
 * Gravity is removed from the vertical component of the windowed acceleration; the component of the
 * remaining acceleration along the velocity is the deceleration due to drag and everything
 * perpendicular to it is due to lift. Each acceleration becomes a coefficient through the dynamic
 * pressure `q = ρ·V²/2`, with ρ the ISA barometric air density at the given altitude:
 * `C = m·a / (q·S)`.
 */
final private[metrics] class Aerodynamics private (config: MetricsConfig) {

  private val GravityVector: GeoVector =
    GeoVector(0.0, 0.0, Gravity)

  /**
   * Lift coefficient `m·|a − proj·v̂| / (q·S)`.
   */
  def liftCoefficient(acceleration: GeoVector, speed: GeoVector, altitude: Double): Double =
    coefficients(acceleration, speed, altitude)._1

  /**
   * Drag coefficient `m·|proj| / (q·S)`.
   */
  def dragCoefficient(acceleration: GeoVector, speed: GeoVector, altitude: Double): Double =
    coefficients(acceleration, speed, altitude)._2

  private def coefficients(
    acceleration: GeoVector,
    speed: GeoVector,
    altitude: Double
  ): (Double, Double) =
    val vel = speed.magnitude

    if vel == 0.0 then
      (0.0, 0.0)
    else
      // Gravity acts on the vertical component; what remains is the non-gravitational acceleration
      val accelCorrected = acceleration - GravityVector

      // Component of the gravity-corrected acceleration along the flight path: deceleration due to drag
      val proj = (accelCorrected dot speed) / vel

      // Drag
      val drag      = speed * proj / vel
      val accelDrag = drag.magnitude

      // Everything perpendicular to the flight path is due to lift
      val lift      = accelCorrected - drag
      val accelLift = lift.magnitude

      val dynamicPressure = airDensity(altitude) * vel * vel / 2.0
      val liftCoefficient = config.mass.value * accelLift / dynamicPressure / config.planformArea.value
      val dragCoefficient = config.mass.value * accelDrag / dynamicPressure / config.planformArea.value

      (liftCoefficient, dragCoefficient)

  // ISA troposphere barometric air density (kg/m³) at the given altitude (m).
  private def airDensity(altitude: Double): Double =
    val exponent    = Gravity * MolarMassDryAir / UniversalGasConstant / TemperatureLapseRate
    val pressure    = SeaLevelPressure * pow(1.0 - TemperatureLapseRate * altitude / SeaLevelTemperature, exponent)
    val temperature = SeaLevelTemperature - TemperatureLapseRate * altitude

    pressure / (UniversalGasConstant / MolarMassDryAir) / temperature

}

object Aerodynamics {

  def apply(config: MetricsConfig): Aerodynamics =
    new Aerodynamics(config)

}
