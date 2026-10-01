package com.colofabrix.scala.beesight.config

import java.time.Duration

/**
 * Configuration for the swoop metrics computation.
 *
 * @param pullThreshold Descent rate (m/s) below which the dive is considered over
 * @param rolloutWindow Height above the stop altitude (m) where the rollout search starts
 * @param groundWindow Duration (s) of the window after landing used to estimate ground altitude
 */
final case class SwoopConfig(
  pullThreshold: Speed,
  rolloutWindow: Altitude,
  groundWindow: Duration,
)
