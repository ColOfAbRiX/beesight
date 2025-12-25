package com.colofabrix.scala.declinio

import cats.effect.*

/**
 * Decline-ready application that uses Cats' IO
 */
trait IODeclineApp[A] extends IOApp with DeclineApp[IO, A]:

  final override def run(args: List[String]): IO[ExitCode] =
    runDeclineApp(args)
