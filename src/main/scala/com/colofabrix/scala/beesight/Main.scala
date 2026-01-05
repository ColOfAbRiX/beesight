package com.colofabrix.scala.beesight

import cats.effect.*
import cats.effect.implicits.given
import cats.implicits.given
import com.colofabrix.scala.beesight.config.*
import com.colofabrix.scala.beesight.debug.ChartGenerator
import com.colofabrix.scala.beesight.files.*
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.declinio.*
import com.monovore.decline.Opts
import java.nio.file.*
import scala.jdk.CollectionConverters.*

object Main extends IODeclineReaderApp[Config] {

  val name: String =
    "beesight"

  val header: String =
    "Beesight - A Flysight data manipulator tool"

  val options: Opts[Config] =
    CliConfig.allOptions

  override def runWithReader: IOConfig[ExitCode] =
    for
      config         <- IOConfig.ask
      inputFiles     <- FileOps.discoverCsvFiles(config.input).to[IOConfig]
      filesToProcess <- applyLimit(inputFiles)
      _              <- IOConfig.println(s"Found ${inputFiles.size} CSV files, processing ${filesToProcess.size}")
      _              <- filesToProcess.traverse(processFile)
    yield ExitCode.Success

  private def applyLimit(files: List[Path]): IOConfig[List[Path]] =
    IOConfig.ask.map { config =>
      config
        .processLimit
        .fold(files)(limit => files.take(limit))
    }

  private def processFile(inputFile: Path): IOConfig[Unit] =
    for
      outputFile <- FileOps.computeOutputPath(inputFile)
      _          <- IOConfig.println(s"Processing: $inputFile -> $outputFile")
      _          <- IOConfig.blocking(Files.createDirectories(outputFile.getParent))
      result     <- processCsvFile(inputFile, outputFile)
      _          <- IOConfig.println(s"DONE: $inputFile\n")
    yield result

  private def processCsvFile(inputFile: Path, outputFile: Path): IOConfig[Unit] =
    for
      config         <- IOConfig.ask
      csvStream       = CsvFileOps.readCsv[FlysightPoint](inputFile)
      flightPoints   <- FlightStagesDetection.detect(csvStream).to[IOConfig]
      chartPath       = FileOps.computeChartPath(outputFile)
      _              <- ChartGenerator.generate(csvStream, flightPoints, chartPath).to[IOConfig]
      outputCsvStream = csvStream.through(DataCutter(config).cut(flightPoints))
      _              <- CsvFileOps.writeData(outputCsvStream, outputFile)
    yield ()

}
