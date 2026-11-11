package com.colofabrix.scala.beesight.metadata

import cats.effect.IO
import cats.syntax.all.*
import com.colofabrix.scala.beesight.IOConfig
import com.colofabrix.scala.beesight.metadata.StoredConversions.*
import com.colofabrix.scala.beesight.pipeline.JumpEventsStage
import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path }
import com.colofabrix.scala.beesight.model.WorkingPaths
import com.colofabrix.scala.beesight.output.CsvFileCutter

/**
 * Loads and saves metadata files, treating missing, stale or corrupt files as an empty cache.
 */
object MetadataFileStore {

  val version: String =
    "1.0.0"

  def load(allPaths: WorkingPaths): IO[MetadataFile] =
    if !Files.isRegularFile(allPaths.metadataFile) then
      IO.pure(MetadataFile.forInput(version))
    else
      for {
        content <- IO.blocking(Files.readString(allPaths.metadataFile, StandardCharsets.UTF_8))
      } yield {
        MetadataFileJson
          .decode(content)
          .toOption
          .filter(isFor(_, version))
          .getOrElse(MetadataFile.forInput(version))
      }

  def save(
    events: JumpEventsStage.DetectedEvents[?],
    cut: CsvFileCutter.CutResult[?],
    allPaths: WorkingPaths,
  ): IOConfig[Unit] =
    IOConfig.ask.flatMap { config =>
      val updated = MetadataFile(
        version,
        events.configHash,
        Some(events.storedEvents),
        Some(cut.summary.toStored),
      )

      save(updated, allPaths.metadataFile).whenA(!config.global.dryRun).to[IOConfig]
    }

  def save(metadataFile: MetadataFile, metadataFilePath: Path): IO[Unit] =
    IO
      .blocking {
        Option(metadataFilePath.getParent) foreach { dir =>
          Files.createDirectories(dir)
        }

        Files.writeString(metadataFilePath, MetadataFileJson.encodeToString(metadataFile), StandardCharsets.UTF_8)
      }
      .as(())

  private def isFor(file: MetadataFile, version: String): Boolean =
    version === file.beesightVersion

}
