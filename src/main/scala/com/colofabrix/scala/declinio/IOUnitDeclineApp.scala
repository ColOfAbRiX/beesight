package com.colofabrix.scala.declinio

import cats.effect.*
import com.monovore.decline.*

/**
 * Decline-ready application that uses Cats' IO and no configuration
 */
trait IOUnitDeclineApp extends IODeclineApp[Unit] {

  final override def options: Opts[Unit] =
    Opts.unit

  final override def runWithConfig(config: Unit): IO[ExitCode] =
    runNoConfig()

  def runNoConfig(): IO[ExitCode]

}
