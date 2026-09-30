package com.colofabrix.scala.beesight.metrics.computations

import com.colofabrix.scala.beesight.config.MetricsConfig
import com.colofabrix.scala.beesight.model.EventPoint

/**
 * Ground elevation resolution for metrics computation.
 */
// FIXME: This is not used anywhere. I think we should start using it
final private[metrics] class MetricsGround private (config: MetricsConfig) {

  /**
   * Resolve the ground elevation in m (MSL).
   */
  def detect(landing: Option[EventPoint]): Double =
    landing
      .map(_.altitude)
      .orElse(config.groundElevation.map(_.value))
      .getOrElse(0.0)

}

object MetricsGround {

  def apply(config: MetricsConfig): MetricsGround =
    new MetricsGround(config)

  /** Convenience: resolve ground elevation without constructing a [[MetricsGround]] instance. */
  def detect(landing: Option[EventPoint], config: MetricsConfig): Double =
    new MetricsGround(config).detect(landing)

}
