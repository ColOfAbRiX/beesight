package com.colofabrix.scala.beesight

import cats.effect.*

object Main extends IOApp:

  def run(args: List[String]): IO[ExitCode] =
    IO(println(s"Hello, World!")).as(ExitCode.Success)
