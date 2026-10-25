package com.colofabrix.scala.beesight.metadata

import cats.effect.IO
import cats.syntax.eq.*
import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path }
import com.colofabrix.scala.beesight.output.WorkingPaths

/**
 * Loads and saves sidecar files, treating missing, stale or corrupt files as an empty cache.
 */
object SidecarStore {

  val version: String =
    "1.0.0"

  def load(allPaths: WorkingPaths): IO[Sidecar] =
    InputFingerprint.of(allPaths.inputFile).flatMap { fingerprint =>
      val empty = Sidecar.forInput(version, fingerprint)

      if !Files.isRegularFile(allPaths.sidecar) then
        IO.pure(empty)
      else
        for {
          content <- IO.blocking(Files.readString(allPaths.sidecar, StandardCharsets.UTF_8))
        } yield {
          SidecarJson
            .decode(content)
            .toOption
            .filter(isFor(_, version, fingerprint))
            .getOrElse(empty)
        }
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

  private def isFor(sidecar: Sidecar, version: String, fingerprint: InputFingerprint): Boolean =
    version === sidecar.beesightVersion && fingerprint === sidecar.inputFingerprint

}
