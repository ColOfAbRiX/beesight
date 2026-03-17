package com.colofabrix.scala.beesight

import cats.effect.IO
import cats.syntax.all.*
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
 * Handles the processing of individual CSV files through the flight data pipeline.
 */
private[beesight] object FileProcessor {

  /**
   * Detects the format of a CSV file and processes it through the appropriate pipeline
   */
  def processFile(inputFile: Path): IOConfig[(Path, Option[OutputFlightPoint[?]])] =
    FormatDetector
      .detect(inputFile)
      .flatMap {
        case FileFormat.FlySight => processFileAs[FlysightPoint](inputFile)
        case FileFormat.Airlog   => processFileAs[AirlogPoint](inputFile)
      }
      .handleErrorWith { error =>
        s"ERROR processing $inputFile: ${error.getMessage}"
          .stderr
          .as((inputFile, None))
      }

  private def processFileAs[A](
    inputFile: Path,
  )(using
    adapter: FileFormatAdapter[A],
    decoder: CsvRowDecoder[A, String],
    encoder: CsvRowEncoder[OutputFlightPoint[A], String],
  ): IOConfig[(Path, Option[OutputFlightPoint[?]])] =
    for
      outputFile <- FileOps.createProcessedDirectory(inputFile)
      _          <- s"Processing: $inputFile -> $outputFile".stdout
      csvStream   = CsvFileReader.readCsv[A](inputFile)
      cfg        <- IOConfig.ask[IO, Config]
      lastPoint  <- processingPipeline(cfg, csvStream, outputFile).compile.last
      result      = (inputFile, lastPoint)
    yield result

  private def processingPipeline[A](
    cfg: Config,
    csvStream: fs2.Stream[IOConfig, A],
    outputCsvFile: Path,
  )(using
    adapter: FileFormatAdapter[A],
    encoder: CsvRowEncoder[OutputFlightPoint[A], String],
  ): fs2.Stream[IOConfig, OutputFlightPoint[A]] =
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
