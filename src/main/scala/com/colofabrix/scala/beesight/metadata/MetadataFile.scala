package com.colofabrix.scala.beesight.metadata

import io.circe.{ Decoder, Encoder }

/**
 * Metadata file `<base>.csv.meta`
 */
final case class MetadataFile(
  beesightVersion: String,
  configHash: ConfigHash,
  jumpevents: Option[StoredFlightEvents],
  cut: Option[StoredCutSummary],
) derives Encoder.AsObject, Decoder

object MetadataFile {

  def forInput(version: String): MetadataFile =
    MetadataFile(version, ConfigHash.of(""), None, None)

}
