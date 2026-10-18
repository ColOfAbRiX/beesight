package com.colofabrix.scala.beesight.metadata

import cats.Eq
import cats.syntax.eq.*
import io.circe.{ Decoder, Encoder }
import java.nio.file.{ Files, Path }

/** Metadata sidecar `<base>.csv.meta`, a per-section analysis cache for one input file. */
final case class Sidecar(
  beesightVersion: String,
  inputFingerprint: InputFingerprint,
  sections: SidecarSections,
) derives Encoder.AsObject, Decoder

object Sidecar {

  def forInput(version: String, fingerprint: InputFingerprint): Sidecar =
    Sidecar(version, fingerprint, SidecarSections())

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

/** The analysis sections cached in the sidecar, each independently versioned by its configuration. */
final case class SidecarSections(
  jumpevents: Option[JumpEventsSection] = None,
  swoop: Option[SwoopSection] = None,
  cut: Option[CutSection] = None,
) derives Encoder.AsObject, Decoder

object SidecarSections {

  extension (self: SidecarSections) {

    def freshJumpevents(configHash: String): Option[JumpEventsSection] =
      self.jumpevents.filter(_.configHash === configHash)

    def freshSwoop(configHash: String): Option[SwoopSection] =
      self.swoop.filter(_.configHash === configHash)

    def freshCut(configHash: String): Option[CutSection] =
      self.cut.filter(_.configHash === configHash)

  }

}

/** Cached result of the jump-events detection. */
final case class JumpEventsSection(
  configHash: String,
  events: StoredFlightEvents
) derives Encoder.AsObject, Decoder

/** Cached result of the swoop analysis. */
final case class SwoopSection(
  configHash: String,
  result: StoredSwoopData
) derives Encoder.AsObject, Decoder

/** Cached result of the cut operation. */
final case class CutSection(
  configHash: String,
  summary: StoredCutSummary
) derives Encoder.AsObject, Decoder

/** Fingerprint of the input file, used to invalidate the whole sidecar. */
final case class InputFingerprint(
  sizeBytes: Long,
  lastModifiedMillis: Long
) derives Encoder.AsObject, Decoder

object InputFingerprint {

  given Eq[InputFingerprint] = 
    Eq.fromUniversalEquals

  def of(file: Path): InputFingerprint =
    InputFingerprint(Files.size(file), Files.getLastModifiedTime(file).toMillis)

}
