package com.colofabrix.scala.beesight.metadata

import io.circe.{ Decoder, Encoder }

/**
 * The analysis sections cached in the sidecar, each independently versioned by its configuration.
 */
final case class SidecarSections(
  jumpevents: Option[JumpEventsSection],
  metrics: Option[MetricsSection],
  swoop: Option[SwoopSection],
  cut: Option[CutSection],
) derives Encoder.AsObject, Decoder

object SidecarSections {

  val empty: SidecarSections =
    SidecarSections(None, None, None, None)

}

/**
 * Cached result of the jump-events detection.
 */
final case class JumpEventsSection(
  configHash: ConfigHash,
  events: StoredFlightEvents
) derives Encoder.AsObject, Decoder

/**
 * Cached result of the per-point flight metrics computation.
 */
final case class MetricsSection(
  configHash: ConfigHash,
  metrics: List[StoredMetric],
) derives Encoder.AsObject, Decoder

/**
 * Cached result of the swoop analysis.
 */
final case class SwoopSection(
  configHash: ConfigHash,
  result: StoredSwoopData
) derives Encoder.AsObject, Decoder

/**
 * Cached result of the cut operation.
 */
final case class CutSection(
  configHash: ConfigHash,
  summary: StoredCutSummary
) derives Encoder.AsObject, Decoder
