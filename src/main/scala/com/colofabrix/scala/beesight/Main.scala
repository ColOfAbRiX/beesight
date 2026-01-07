package com.colofabrix.scala.beesight

import cats.effect.*
import cats.effect.implicits.given
import cats.implicits.given
import com.colofabrix.scala.beesight.config.*
import com.colofabrix.scala.beesight.debug.*
import com.colofabrix.scala.beesight.files.*
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.declinio.*
import com.monovore.decline.Opts
import java.nio.file.*

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
      dataCutter <- DataCutter()
      csvStream   = CsvFileOps.readCsv[FlysightPoint](inputFile)
      pipeline   <- buildPipeline(csvStream, dataCutter, outputFile)
      result     <- pipeline.compile.drain.to[IOConfig]
      _          <- s"DONE: $inputFile\n".stdout
    yield result

  private def buildPipeline(
    csvStream: fs2.Stream[IO, FlysightPoint],
    dataCutter: DataCutter,
    outputFile: Path,
  ): IOConfig[fs2.Stream[IO, Unit]] =
    IOConfig
      .ask
      .map { config =>
        csvStream
          .map(InputFlightPoint.fromFlysightPoint)
          .through(FlightStagesDetection.streamDetect)
          .through(dataCutter.cutPipe)
          .broadcastThrough(
            CsvFileOps.writeCsvPipe(outputFile, config.dryRun),
            ChartGenerator.chartPipe(outputFile)(_.hMSL, _.velD),
            ResultPrinter.printStagesPipe,
          )
      }

}
