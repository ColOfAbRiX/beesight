package com.colofabrix.scala.beesight

import cats.effect.IO
import cats.syntax.all.*
import com.colofabrix.scala.beesight.config.{ Command, Config }
import com.colofabrix.scala.beesight.detection.jumpevents.*
import com.colofabrix.scala.beesight.detection.readers.*
import com.colofabrix.scala.beesight.formats.*
import com.colofabrix.scala.beesight.formats.convert.*
import com.colofabrix.scala.beesight.formats.devices.*
import com.colofabrix.scala.beesight.input.*
import com.colofabrix.scala.beesight.output.*
import fs2.data.csv.*
import fs2.io.file.{ Files, Path as Fs2Path }
import java.nio.file.*

/**
 * Handles the processing of individual CSV files through the flight data pipeline.
 */
private[beesight] object FileProcessor {

  /**
   * Detects the format of a CSV file and processes it with the pipeline of the selected command
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

  private def detectFormat(filePath: Path): IOConfig[FileFormat] =
    for {
      headerLine <- CsvFileReader.readHeaderLine(filePath)
      detect      = FormatDetector.detect(headerLine)
      result     <- detect.toIOConfig(new IllegalArgumentException(s"Unknown CSV format in '$filePath'"))
    } yield result

  private def processFileAs[A: PointReader: FlysightReader](
    inputFile: Path,
  )(using
    CsvRowDecoder[A, String],
    CsvRowEncoder[A, String],
  ): IOConfig[Unit] =
    for
      cfg            <- IOConfig.ask[IO, Config]
      outputFilePath <- FileOps.createProcessedDirectory(inputFile)
      _              <- s"Processing: $inputFile -> $outputFilePath".stdout
      inputCsvStream  = CsvFileReader.readCsv[A](inputFile)
      _              <- runCommand(cfg, inputCsvStream, outputFilePath).compile.last
    yield ()

  private def runCommand[A: PointReader: FlysightReader](
    cfg: Config,
    inputCsvStream: fs2.Stream[IOConfig, A],
    outputCsvPath: Path,
  )(using
    CsvRowEncoder[A, String],
  ): fs2.Stream[IOConfig, Unit] =
    cfg.command match {
      case Command.Convert =>
        FileConverter.conversionPipeline(inputCsvStream, outputCsvPath, cfg.global.dryRun)
      case _ =>
        processingPipeline(cfg, inputCsvStream, outputCsvPath)
    }

  private def processingPipeline[A: PointReader](
    cfg: Config,
    csvStream: fs2.Stream[IOConfig, A],
    outputCsvPath: Path,
  )(using
    CsvRowEncoder[A, String],
  ): fs2.Stream[IOConfig, Unit] = {
    val detector     = JumpEventsDetector(cfg.detectionConfig.jumpevents)
    val eventsStream = csvStream.through(detector.detect)

    (eventsStream zip csvStream)
      .through(DataCutter(cfg).cutPipe)
      .broadcastThrough(
        CsvResultWriter(cfg).writePipe(outputCsvPath),
        ChartGenerator(cfg).chartPipe(outputCsvPath),
        ResultPrinter(cfg).printStagesPipe,
      )
  }

}
