package com.colofabrix.scala.declinio

import cats.data.ReaderT
import cats.effect.*

/**
 * Decline application for any effect F[_] and configuration A that uses a run method to pass the configuration
 */
trait DeclineApp[F[_], A] extends IOApp with DeclineReaderApp[F, A]:

  /**
   * Application main method that receives the compiled configuration
   */
  def runWithConfig(config: A): F[ExitCode]

  final override def runWithReader: ReaderT[F, A, ExitCode] =
    ReaderT(runWithConfig)
