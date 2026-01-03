package com.colofabrix.scala.declinio

import cats.effect.*
import com.monovore.decline.*

/**
 * Decline application for Cats' IO that uses a run method and no configuration
 */
trait IOUnitDeclineApp extends IODeclineApp[Unit] {

  /**
   * Application main method that does not use configuration
   */
  def runNoConfig: IO[ExitCode]

  final override def options: Opts[Unit] =
    Opts.unit

  final override def runWithConfig(config: Unit): IO[ExitCode] =
    runNoConfig

}
