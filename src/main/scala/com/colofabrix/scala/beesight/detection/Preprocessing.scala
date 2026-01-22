package com.colofabrix.scala.beesight.detection

import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.model.InputFlightRow
import java.time.Duration
import com.colofabrix.scala.beesight.detection.model.StreamState

/**
 * Preprocessing functions to clean and validate input data before detection.
 */
object Preprocessing {

  /**
   * Preprocess raw input data to remove spikes and anomalies.
   */
  def process[A](rawPoint: InputFlightRow[A], prevState: StreamState[A], config: DetectionConfig): InputFlightRow[A] =
    val dt = durationSeconds(prevState.kinematics.time, rawPoint.time)

    if dt <= 0 then
      rawPoint
    else
      val maxDeltaSpeed = config.MaxAcceleration * dt

      // Clip vertical speed based on max allowed acceleration
      val clippedVertSpeed =
        clipValue(
          rawPoint.verticalSpeed,
          prevState.kinematics.verticalSpeed,
          maxDeltaSpeed,
        )

      // Clip horizontal speeds
      val clippedNorthSpeed =
        clipValue(
          rawPoint.northSpeed,
          prevState.kinematics.northSpeed,
          maxDeltaSpeed,
        )
      val clippedEastSpeed =
        clipValue(
          rawPoint.eastSpeed,
          prevState.kinematics.eastSpeed,
          maxDeltaSpeed,
        )

      // Altitude: if vertical speed was clipped, recalculate from velocity
      // velD is down-positive, so we subtract to get altitude change
      val clippedAltitude =
        if clippedVertSpeed != rawPoint.verticalSpeed then
          prevState.kinematics.altitude - clippedVertSpeed * dt
        else
          rawPoint.altitude

      rawPoint.copy(
        verticalSpeed = clippedVertSpeed,
        northSpeed = clippedNorthSpeed,
        eastSpeed = clippedEastSpeed,
        altitude = clippedAltitude,
      )

  private def clipValue(current: Double, previous: Double, maxDelta: Double): Double =
    val delta = current - previous

    if math.abs(delta) > maxDelta then
      previous + math.signum(delta) * maxDelta
    else
      current

  private def durationSeconds(from: java.time.Instant, to: java.time.Instant): Double =
    Duration.between(from, to).toMillis / 1000.0

}
