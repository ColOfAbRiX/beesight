package com.colofabrix.scala.beesight.metadata

import cats.effect.IO
import cats.Eq
import cats.syntax.eq.*
import io.circe.{ Decoder, Encoder }
import java.nio.file.{ Files, Path }
import java.time.{ OffsetDateTime, ZoneOffset }

/**
 * Metadata sidecar `<base>.csv.meta`, a per-section analysis cache for one input file.
 */
final case class Sidecar(
  beesightVersion: String,
  inputFingerprint: InputFingerprint,
  sections: SidecarSections,
) derives Encoder.AsObject, Decoder

object Sidecar {

  def forInput(version: String, fingerprint: InputFingerprint): Sidecar =
    Sidecar(version, fingerprint, SidecarSections.empty)

}

/**
 * Fingerprint of the input file, used to invalidate the whole sidecar.
 */
final case class InputFingerprint(
  sizeBytes: Long,
  lastModified: OffsetDateTime
) derives Encoder.AsObject, Decoder

object InputFingerprint {

  given Eq[InputFingerprint] =
    Eq.fromUniversalEquals

  def of(file: Path): IO[InputFingerprint] =
    for {
      size         <- IO.blocking(Files.size(file))
      lastModified <- IO.blocking(OffsetDateTime.ofInstant(Files.getLastModifiedTime(file).toInstant, ZoneOffset.UTC))
      result        = InputFingerprint(size, lastModified)
    } yield result

}
