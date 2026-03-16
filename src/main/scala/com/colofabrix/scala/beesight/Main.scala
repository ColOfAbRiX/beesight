package com.colofabrix.scala.beesight

import cats.effect.*
import cats.effect.implicits.given
import cats.implicits.given
import com.colofabrix.scala.beesight.config.*
import com.colofabrix.scala.beesight.input.*
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.output.*
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
    Config.declineOpts

  override def runWithReader: IOConfig[ExitCode] =
    for
      inputFiles  <- FileOps.discoverCsvFiles()
      _           <- s"Processing ${inputFiles.size} CSV files".stdout
      summaryRows <- runParallel(inputFiles)
      _           <- ResultsSummary.writeSummaryCsv(summaryRows)
    yield ExitCode.Success

  private def runParallel(inputFiles: List[Path]): IOConfig[List[(Path, Option[OutputFlightPoint[?]])]] =
    IOConfig.ask.flatMap { config =>
      config
        .parallelism
        .map(_.value)
        .fold(inputFiles.parTraverse(FileProcessor.processFile)) { n =>
          inputFiles.parTraverseN(n)(FileProcessor.processFile)
        }
    }

}
