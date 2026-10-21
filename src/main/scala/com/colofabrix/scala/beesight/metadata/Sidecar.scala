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

  extension (self: Sidecar) {

    def withJumpevents(section: JumpEventsSection): Sidecar =
      self.copy(sections = self.sections.copy(jumpevents = Some(section)))

    def withSwoop(section: SwoopSection): Sidecar =
      self.copy(sections = self.sections.copy(swoop = Some(section)))

    def withCut(section: CutSection): Sidecar =
      self.copy(sections = self.sections.copy(cut = Some(section)))

    /** Whether this sidecar still describes the given input file and was produced by the given version. */
    def isFor(version: String, fingerprint: InputFingerprint): Boolean =
      version === self.beesightVersion && fingerprint === self.inputFingerprint

  }

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
