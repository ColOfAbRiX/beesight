package com.colofabrix.scala.beesight.pipeline

import com.colofabrix.scala.beesight.*
import com.colofabrix.scala.beesight.IOConfig
import com.colofabrix.scala.beesight.detection.readers.*
import com.colofabrix.scala.beesight.input.CsvFileReader
import com.colofabrix.scala.beesight.metadata.*
import com.colofabrix.scala.beesight.output.*
import fs2.data.csv.{ CsvRowDecoder, CsvRowEncoder }
import java.nio.file.Path

/**
 * Orchestrates the canonical stages of the processing pipeline for one input file, caching the detection results
 * in the metadata file of the file.
 */
object StagesRunner {

  def run[A: PointReader: GpsReader: CsvRowDecoder[*, String]: CsvRowEncoder[*, String]](
    paths: WorkingPaths
  ): IOConfig[Unit] =
    for
      metadataFile <- MetadataFileStore.load(paths).to[IOConfig]
      dataPoints   <- CsvFileReader.readCsv[A](paths.inputFile).compile.toVector
      events       <- JumpEventsStage.run[A](dataPoints, metadataFile)
      metrics      <- MetricsStage.run[A](dataPoints)
      swoop        <- SwoopStage.run[A](dataPoints, events.finalEvents)
      cutDataset   <- DataCutter().map(_.cutVector(events.rows))
      _            <- StageOutputs.run[A](cutDataset.retained, events.finalEvents, metrics.metrics, swoop.swoop, paths)
      _            <- MetadataFileStore.save(events, cutDataset, paths)
    yield ()

}
