package com.colofabrix.scala.beesight.pipeline

import cats.syntax.all.*
import com.colofabrix.scala.beesight.*
import com.colofabrix.scala.beesight.IOConfig
import com.colofabrix.scala.beesight.config.Config
import com.colofabrix.scala.beesight.detection.readers.*
import com.colofabrix.scala.beesight.input.CsvFileReader
import com.colofabrix.scala.beesight.metadata.*
import com.colofabrix.scala.beesight.metadata.ConfigHash.*
import com.colofabrix.scala.beesight.metadata.StoredConversions.*
import com.colofabrix.scala.beesight.output.*
import fs2.data.csv.{ CsvRowDecoder, CsvRowEncoder }
import java.nio.file.Path

/**
 * Orchestrates the canonical stages of the processing pipeline for one input file, caching the analysis in the
 * sidecar of the file.
 */
object StagesRunner {

  def run[A: PointReader: GpsReader: CsvRowDecoder[*, String]: CsvRowEncoder[*, String]](paths: WorkingPaths): IOConfig[Unit] =
    for
      config     <- IOConfig.ask
      oldSidecar <- SidecarStore.load(paths).to[IOConfig]
      dataPoints <- CsvFileReader.readCsv[A](paths.inputFile).compile.toVector
      events     <- JumpEventsStage.run[A](dataPoints, oldSidecar)
      metrics    <- MetricsStage.run[A](dataPoints, oldSidecar)
      swoop      <- SwoopStage.run[A](dataPoints, events.finalEvents, oldSidecar)

      cutResult   = DataCutter(config).cutWithSummary(events.rows)
      cutSection  = CutSection(config.dataCutterConfig.configHash, cutResult.summary.toStored)
      newSidecar  = mergeSections(oldSidecar, events.section, cutSection, metrics.section, swoop.section)
      _          <- StageOutputs.run[A](cutResult.retained, events.finalEvents, metrics.metrics, swoop.swoop, paths)
      _          <- SidecarStore.save(newSidecar, paths.sidecar).to[IOConfig].whenA(!config.global.dryRun)
    yield ()

  private def mergeSections(
    sidecar: Sidecar,
    jumpevents: JumpEventsSection,
    cut: CutSection,
    metrics: Option[MetricsSection],
    swoop: Option[SwoopSection],
  ): Sidecar =
    val newSections =
      sidecar.sections.copy(
        jumpevents = Some(jumpevents),
        metrics = metrics.orElse(sidecar.sections.metrics),
        swoop = swoop.orElse(sidecar.sections.swoop),
        cut = Some(cut),
      )

    sidecar.copy(sections = newSections)

}
