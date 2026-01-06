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
      inputFiles     <- FileOps.discoverCsvFiles()
      filesToProcess <- applyLimit(inputFiles)
      _              <- s"Found ${inputFiles.size} CSV files, processing ${filesToProcess.size}".stdout
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
      outputFile <- FileOps.createOutputFile(inputFile)
      _          <- s"Processing: $inputFile -> $outputFile".stdout
      result     <- processCsvFile(inputFile, outputFile)
      _          <- s"DONE: $inputFile\n".stdout
    yield result

  private def processCsvFile(inputFile: Path, outputFile: Path): IOConfig[Unit] =
    for
      config       <- IOConfig.ask
      csvStream     = CsvFileOps.readCsv[FlysightPoint](inputFile)
      flightPoints <- FlightStagesDetection.detect(csvStream).to[IOConfig]
      cutStream     = csvStream.through(DataCutter(config).cut(flightPoints))
      _            <- processOutputsStream(cutStream, flightPoints, outputFile)
    yield ()

  private def processOutputsStream(
    stream: fs2.Stream[IO, FlysightPoint],
    points: FlightStagesPoints,
    outputFile: Path,
  ): IOConfig[Unit] =
    IOConfig.ask.flatMap { config =>
      stream
        .broadcastThrough(
          CsvFileOps.writeIntoCsv(outputFile, config.dryRun),
          ChartGenerator.chartPipe(points, outputFile),
        )
        .compile
        .drain
        .to[IOConfig]
    }

}
