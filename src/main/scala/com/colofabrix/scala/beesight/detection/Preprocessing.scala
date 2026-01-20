package com.colofabrix.scala.beesight.detection

import com.colofabrix.scala.beesight.model.InputFlightPoint
import com.colofabrix.scala.beesight.config.DetectionConfig.default.*
import java.time.Duration

/**
 * Preprocessing functions to clean and validate input data before detection.
 * Clips implausible values based on physical limits (max acceleration).
 */
object Preprocessing {

  /**
   * Clips velocity and altitude values that exceed physical limits based on max acceleration.
   * Uses StreamState for previous values to detect implausible changes.
   *
   * @param current The current raw input point
   * @param prevState The previous StreamState containing previous values
   * @return A new InputFlightPoint with clipped values if needed
   */
  def preprocessData[A](
    current: InputFlightPoint[A],
    prevState: StreamState[A],
  ): InputFlightPoint[A] =
    val dt = durationSeconds(prevState.time, current.time)

    if dt <= 0 then
      current
    else
      val maxDeltaSpeed = MaxAcceleration * dt

      // Clip vertical speed
      val clippedVertSpeed =
        clipValue(
          current.verticalSpeed,
          prevState.verticalSpeed,
          maxDeltaSpeed,
        )

      // Clip horizontal speeds
      val clippedNorthSpeed =
        clipValue(
          current.northSpeed,
          prevState.inputPoint.northSpeed,
          maxDeltaSpeed,
        )
      val clippedEastSpeed =
        clipValue(
          current.eastSpeed,
          prevState.inputPoint.eastSpeed,
          maxDeltaSpeed,
        )

      // Altitude: if vertical speed was clipped, recalculate from velocity
      // velD is down-positive, so we subtract to get altitude change
      val clippedAltitude =
        if clippedVertSpeed != current.verticalSpeed then
          prevState.height - clippedVertSpeed * dt
        else
          current.altitude

      current.copy(
        verticalSpeed = clippedVertSpeed,
        northSpeed = clippedNorthSpeed,
        eastSpeed = clippedEastSpeed,
        altitude = clippedAltitude,
      )

  /**
   * Clips a value to within maxDelta of the previous value.
   */
  private def clipValue(current: Double, previous: Double, maxDelta: Double): Double =
    val delta = current - previous
    if math.abs(delta) > maxDelta then
      previous + math.signum(delta) * maxDelta
    else
      current

  /**
   * Calculates duration between two instants in seconds.
   */
  private def durationSeconds(from: java.time.Instant, to: java.time.Instant): Double =
    Duration.between(from, to).toMillis / 1000.0

}
