package com.colofabrix.scala.beesight

import cats.effect.IO
import com.colofabrix.scala.beesight.config.Config
import com.colofabrix.scala.beesight.detection.*
import com.colofabrix.scala.beesight.input.*
import com.colofabrix.scala.beesight.input.formats.*
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.model.formats.{ AirlogPoint, FileFormatAdapter, FlysightPoint }
import com.colofabrix.scala.beesight.output.*
import fs2.data.csv.*
import java.nio.file.*

/**
 * Handles the processing of individual CSV files through the flight data pipeline
 */
object FileProcessor {

  /**
   * Detects the format of a CSV file and processes it through the appropriate pipeline
   */
  def processFile(inputFile: Path): IOConfig[(Path, Option[OutputFlightPoint[?]])] =
    FormatDetector.detect(inputFile).flatMap {
      case FileFormat.FlySight => processFileAs[FlysightPoint](inputFile)
      case FileFormat.Airlog   => processFileAs[AirlogPoint](inputFile)
    }

  private def processFileAs[A](
    inputFile: Path,
  )(using
    decoder: CsvRowDecoder[A, String],
    adapter: FileFormatAdapter[A],
    encoder: CsvRowEncoder[OutputFlightPoint[A], String],
  ): IOConfig[(Path, Option[OutputFlightPoint[?]])] =
    for
      outputFile <- FileOps.createProcessedDirectory(inputFile)
      _          <- s"Processing: $inputFile -> $outputFile".stdout
      csvStream   = CsvFileReader.readCsv[A](inputFile)
      pipeline   <- processingPipeline(csvStream, outputFile)
      lastPoint  <- pipeline.compile.last
    yield (inputFile, lastPoint)

  private def processingPipeline[A](
    csvStream: fs2.Stream[IOConfig, A],
    outputCsvFile: Path,
  )(using
    adapter: FileFormatAdapter[A],
    encoder: CsvRowEncoder[OutputFlightPoint[A], String],
  ): IOConfig[fs2.Stream[IOConfig, OutputFlightPoint[A]]] =
    IOConfig
      .ask[IO, Config]
      .map { cfg =>
        csvStream
          .through(FlightStagesDetection(cfg.detectionConfig).streamDetect)
          .through(DataCutter(cfg).cutPipe)
          .broadcastThrough(
            CsvResultWriter(cfg).writePipe(outputCsvFile),
            ChartGenerator(cfg).chartPipe(outputCsvFile),
            ResultPrinter(cfg).printStagesPipe,
            identity,
          )
      }

}
