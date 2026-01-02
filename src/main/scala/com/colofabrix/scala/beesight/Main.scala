package com.colofabrix.scala.beesight

import cats.effect.*
import cats.effect.implicits.given
import cats.implicits.given
import com.colofabrix.scala.beesight.config.*
import com.colofabrix.scala.beesight.debug.ChartGenerator
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.declinio.IODeclineApp
import com.monovore.decline.Opts
import java.nio.file.*
import scala.jdk.CollectionConverters.*

object Main extends IODeclineApp[Config] {

  val name: String =
    "beesight"

  val header: String =
    "Beesight - A Flysight data cleaner and manipulator tool"

  val options: Opts[Config] =
    CliConfig.allOptions

  override def runWithConfig(config: Config): IO[ExitCode] = {
    for
      inputFiles     <- FileOps.discoverCsvFiles(config.input).to[IOConfig]
      filesToProcess <- applyLimit(inputFiles)
      _              <- IOConfig.println(s"Found ${inputFiles.size} CSV files, processing ${filesToProcess.size}")
      _              <- filesToProcess.traverse(processFile)
    yield ExitCode.Success
  }.run(config)

  private def applyLimit(files: List[Path]): IOConfig[List[Path]] =
    IOConfig.mapConfig { config =>
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
    val csvStream = CsvFileOps.readCsv[FlysightPoint](inputFile)
    val chartPath = computeChartPath(outputFile)

    for
      flightPoints <- FlightStagesDetection.detect(csvStream).to[IOConfig]
      _            <- ChartGenerator.generate(csvStream, flightPoints, chartPath).to[IOConfig]
      _            <- CsvFileOps.writeData(csvStream, flightPoints, outputFile)
    yield ()

  private def computeChartPath(outputFile: Path): Path =
    val baseName =
      outputFile
        .getFileName
        .toString
        .replaceFirst("\\.[^.]+$", "")

    Option(outputFile.getParent)
      .getOrElse(Paths.get("."))
      .resolve(s"$baseName.html")

}
