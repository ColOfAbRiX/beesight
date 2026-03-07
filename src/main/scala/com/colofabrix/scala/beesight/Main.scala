package com.colofabrix.scala.beesight

import cats.effect.*
import cats.effect.implicits.given
import cats.implicits.given
import com.colofabrix.scala.beesight.config.*
import com.colofabrix.scala.beesight.output.*
import com.colofabrix.scala.beesight.detection.*
import com.colofabrix.scala.beesight.files.*
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.model.formats.FlysightPoint
import com.colofabrix.scala.declinio.*
import com.monovore.decline.Opts
import java.nio.file.*

/**
 * Main entry point for the Beesight application.
 */
object Main extends IODeclineReaderApp[Config] {

  val name: String =
    "beesight"

  val header: String =
    "Beesight - A Flysight data manipulator tool"

  val options: Opts[Config] =
    CliConfig.allOptions

  override def runWithReader: IOConfig[ExitCode] =
    IOConfig.timed("total") {
      for
        inputFiles  <- FileOps.discoverCsvFiles()
        _           <- s"Processing ${inputFiles.size} CSV files".stdout
        summaryRows <- runParallel(inputFiles)
        _           <- ResultsSummary.writeSummaryCsv(summaryRows)
      yield ExitCode.Success
    }

  private def runParallel(inputFiles: List[Path]): IOConfig[List[(Path, Option[OutputFlightPoint[FlysightPoint]])]] =
    IOConfig.ask.flatMap { config =>
      config
        .parallelism
        .fold(inputFiles.parTraverse(processFile))(inputFiles.parTraverseN(_)(processFile))
    }

  private def processFile(inputFile: Path): IOConfig[(Path, Option[OutputFlightPoint[FlysightPoint]])] =
    for
      outputFile <- FileOps.createProcessedDirectory(inputFile)
      _          <- s"Processing: $inputFile -> $outputFile".stdout
      csvStream   = CsvFileReader.readCsv[FlysightPoint](inputFile)
      pipeline   <- processingPipeline(csvStream, outputFile)
      lastPoint  <- pipeline.compile.last
    yield (inputFile, lastPoint)

  private def processingPipeline(
    csvStream: fs2.Stream[IOConfig, FlysightPoint],
    outputCsvFile: Path,
  ): IOConfig[fs2.Stream[IOConfig, OutputFlightPoint[FlysightPoint]]] =
    IOConfig
      .ask
      .map { config =>
        csvStream
          .through(FlightStagesDetection(DetectionConfig.default).streamDetect)
          .through(DataCutter(config).cutPipe)
          .broadcastThrough(
            CsvResultWriter(config).writePipe(outputCsvFile),
            ChartGenerator(config).chartPipe(outputCsvFile),
            ResultPrinter(config).printStagesPipe,
            identity,
          )
      }

}
