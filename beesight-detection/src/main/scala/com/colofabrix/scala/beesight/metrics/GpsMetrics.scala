package com.colofabrix.scala.beesight.metrics

/**
 * GPS-quality metrics derived from the raw accuracy fields.
 */
private[metrics] object GpsMetrics {

  /**
   * Spherical error probable: 3D 95th-percentile position error estimate (m).
   */
  def sphericalErrorProbable(horizontalAccuracy: Double, verticalAccuracy: Double): Double =
    0.5127 * (2.0 * horizontalAccuracy + verticalAccuracy)

  /**
   * Accuracy of the 3-second speed-score average (m/s), derived from the vertical accuracy.
   */
  def speedScoreAccuracy(verticalAccuracy: Double): Double =
    Math.sqrt(2.0) * verticalAccuracy / 3.0

}
