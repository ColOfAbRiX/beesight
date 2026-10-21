package com.colofabrix.scala.beesight.metadata

import cats.effect.IO
import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path }

/**
 * Loads and saves sidecar files, treating missing, stale or corrupt files as an empty cache.
 */
object SidecarStore {

  val version: String =
    "1.0.0"

  def load(sidecarFile: Path, fingerprint: InputFingerprint): IO[Sidecar] =
    val empty = Sidecar.forInput(version, fingerprint)

    if !Files.isRegularFile(sidecarFile) then
      IO.pure(empty)
    else
      for {
        content <- IO.blocking(Files.readString(sidecarFile, StandardCharsets.UTF_8))
      } yield {
        SidecarJson
          .decode(content)
          .toOption
          .filter(_.isFor(version, fingerprint))
          .getOrElse(empty)
      }

  def save(sidecar: Sidecar, sidecarFile: Path): IO[Unit] =
    IO
      .blocking {
        Option(sidecarFile.getParent) foreach { dir =>
          Files.createDirectories(dir)
        }

        Files.writeString(sidecarFile, SidecarJson.encodeToString(sidecar), StandardCharsets.UTF_8)
      }
      .as(())

}
