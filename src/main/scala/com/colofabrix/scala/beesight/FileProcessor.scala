package com.colofabrix.scala.beesight

import cats.effect.IO
import cats.syntax.all.*
import com.colofabrix.scala.beesight.config.Config
import com.colofabrix.scala.beesight.detection.jumpevents.*
import com.colofabrix.scala.beesight.detection.readers.*
import com.colofabrix.scala.beesight.formats.*
import com.colofabrix.scala.beesight.formats.devices.*
import com.colofabrix.scala.beesight.input.*
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
  def processFile(inputFile: Path): IOConfig[Unit] =
    detectFormat(inputFile)
      .flatMap {
        case FileFormat.FlySight => processFileAs[FlysightPoint](inputFile)
        case FileFormat.Airlog   => processFileAs[AirlogPoint](inputFile)
        case FileFormat.Dekunu   => processFileAs[DekunuPoint](inputFile)
        case FileFormat.Generic  => processFileAs[GenericPoint](inputFile)
      }
      .handleErrorWith { error =>
        s"ERROR processing $inputFile: ${error.getMessage}"
          .stderr
          .as(())
      }

  private def processFileAs[A: PointReader](
    inputFile: Path,
  )(using
    CsvRowDecoder[A, String],
    CsvRowEncoder[A, String],
  ): IOConfig[Unit] =
    for
      outputFile <- FileOps.createProcessedDirectory(inputFile)
      _          <- s"Processing: $inputFile -> $outputFile".stdout
      csvStream   = CsvFileReader.readCsv[A](inputFile)
      cfg        <- IOConfig.ask[IO, Config]
      _          <- processingPipeline(cfg, csvStream, outputFile).compile.last
    yield ()

  private def processingPipeline[A: PointReader](
    cfg: Config,
    csvStream: fs2.Stream[IOConfig, A],
    outputCsvFile: Path,
  )(using
    CsvRowEncoder[A, String],
  ): fs2.Stream[IOConfig, Unit] = {
    val detector     = JumpEventsDetector(cfg.detectionConfig.jumpevents)
    val eventsStream = csvStream.through(detector.detect)

    (eventsStream zip csvStream)
      .through(DataCutter(cfg).cutPipe)
      .broadcastThrough(
        CsvResultWriter(cfg).writePipe(outputCsvFile),
        ChartGenerator(cfg).chartPipe(outputCsvFile),
        ResultPrinter(cfg).printStagesPipe,
      )
  }

  private def detectFormat(filePath: Path): IOConfig[FileFormat] =
    for {
      headerLine <- CsvFileReader.readHeaderLine(filePath)
      detect      = FormatDetector.detect(headerLine)
      result     <- detect.toIOConfig(new IllegalArgumentException(s"Unknown CSV format in '$filePath'"))
    } yield result

}
