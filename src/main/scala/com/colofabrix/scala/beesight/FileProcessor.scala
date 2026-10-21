package com.colofabrix.scala.beesight

import cats.effect.IO
import cats.syntax.all.*
import com.colofabrix.scala.beesight.config.{ Command, Config }
import com.colofabrix.scala.beesight.detection.readers.*
import com.colofabrix.scala.beesight.formats.*
import com.colofabrix.scala.beesight.formats.devices.*
import com.colofabrix.scala.beesight.input.*
import com.colofabrix.scala.beesight.output.*
import com.colofabrix.scala.beesight.pipeline.StagesRunner
import fs2.data.csv.*
import java.nio.file.Path

/**
 * Detects the format of each input file and hands it to the pipeline of the selected command.
 */
private[beesight] object FileProcessor {

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
    for
      headerLine <- CsvFileReader.readHeaderLine(filePath)
      detect      = FormatDetector.detect(headerLine)
      result     <- detect.toIOConfig(new IllegalArgumentException(s"Unknown CSV format in '$filePath'"))
    yield result

  private def processFileAs[A: PointReader: FlysightReader](
    inputFile: Path,
  )(using
    CsvRowDecoder[A, String],
    CsvRowEncoder[A, String],
  ): IOConfig[Unit] =
    for
      cfg          <- IOConfig.ask[IO, Config]
      processedCsv <- FileOps.createProcessedDirectory(inputFile)
      paths         = OutputPaths(inputFile, processedCsv)
      _            <- s"Processing: $inputFile -> $processedCsv".stdout
      _            <- cfg.command match {
        case Command.Convert => FileConverter.convertFile[A](inputFile, paths, cfg.global.dryRun)
        case _               => StagesRunner.run[A](cfg, inputFile, paths)
      }
    yield ()

}
