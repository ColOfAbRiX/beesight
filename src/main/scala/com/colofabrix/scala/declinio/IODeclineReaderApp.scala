package com.colofabrix.scala.declinio

import cats.arrow.FunctionK
import cats.effect.*
import cats.~>

/**
 * Decline application for Cats' IO and configuration A that uses a ReaderT to pass the configuration
 */
trait IODeclineReaderApp[A] extends DeclineReaderApp[IO, A]:

  final override protected def runEffectToIO: IO ~> IO =
    FunctionK.id
