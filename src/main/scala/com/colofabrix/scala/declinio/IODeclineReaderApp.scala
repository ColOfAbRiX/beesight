package com.colofabrix.scala.declinio

import cats.~>
import cats.arrow.FunctionK
import cats.effect.*

/**
 * Decline application for Cats' IO and configuration A that uses a ReaderT to pass the configuration
 */
trait IODeclineReaderApp[A] extends DeclineReaderApp[IO, A]:

  protected final def runEffectToIO: IO ~> IO =
    FunctionK.id
