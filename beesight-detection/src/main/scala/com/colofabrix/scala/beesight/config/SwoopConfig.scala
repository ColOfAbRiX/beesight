package com.colofabrix.scala.beesight.config

/**
 * Configuration for the swoop metrics computation.
 *
 * @param pullThreshold Descent rate (m/s) below which the dive is considered over
 * @param rolloutWindow Height above the stop altitude (m) where the rollout search starts
 */
final case class SwoopConfig(
  pullThreshold: Double,
  rolloutWindow: Double,
)
