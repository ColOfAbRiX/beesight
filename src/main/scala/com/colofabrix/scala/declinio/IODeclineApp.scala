package com.colofabrix.scala.declinio

import cats.arrow.FunctionK
import cats.effect.*
import cats.~>

/**
 * Decline application for Cats' IO and configuration A that uses a run method to pass the configuration
 */
trait IODeclineApp[A] extends DeclineApp[IO, A]:

  final override protected def runEffectToIO: IO ~> IO =
    FunctionK.id
