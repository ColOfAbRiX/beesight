package com.colofabrix.scala.decline

import cats.effect.{ ExitCode, IO, IOApp, Sync }
import cats.effect.std.Console
import cats.syntax.all._
import com.monovore.decline._

trait IODeclineApp[A] extends IOApp with DeclineApp[IO, A]:

  final override def run(args: List[String]): IO[ExitCode] =
    runDeclineApp(args)

trait DeclineApp[F[_]: Sync: Console, A]:

  def name: String
  def options: Opts[A]
  def header: String
  def runWithConfig(config: A): F[ExitCode]

  def helpFlag: Boolean = true
  def version: String   = ""

  def runDeclineApp(args: List[String]): F[ExitCode] =
    val mainOpts    = addVersionFlag(options.map(runWithConfig))
    val mainCommand = Command(name, header, helpFlag)(mainOpts)
    val feedArgs    = PlatformApp.ambientArgs.getOrElse(args)
    val feedEnv     = PlatformApp.ambientEnvs.getOrElse(sys.env)

    for {
      parseResult <- Sync[F].delay(mainCommand.parse(feedArgs, feedEnv))
      exitCode    <- parseResult.fold(printHelp, identity)
    } yield exitCode

  private def printHelp(help: Help): F[ExitCode] =
    Console[F].errorln(help).as {
      if (help.errors.nonEmpty) ExitCode.Error
      else ExitCode.Success
    }

  private def addVersionFlag(opts: Opts[F[ExitCode]]): Opts[F[ExitCode]] =
    Option(version)
      .filter(_.nonEmpty)
      .map { nonNullVersion =>
        Opts
          .flag("version", "Print the version number and exit.", "v", Visibility.Partial)
          .as(Console[F].println(nonNullVersion).as(ExitCode.Success))
          .orElse(opts)
      }
      .getOrElse(opts)
