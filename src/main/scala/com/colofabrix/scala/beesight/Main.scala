package com.colofabrix.scala.beesight

import cats.effect.*
import com.colofabrix.scala.beesight.cliutils.*
import com.colofabrix.scala.beesight.config.*
import com.colofabrix.scala.beesight.input.*
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
      inputFiles <- FileOps.discoverCsvFiles()
      _          <- s"Processing ${inputFiles.size} CSV files".stdout
      _          <- inputFiles.configTraverse(FileProcessor.processFile)
    yield ExitCode.Success

}
