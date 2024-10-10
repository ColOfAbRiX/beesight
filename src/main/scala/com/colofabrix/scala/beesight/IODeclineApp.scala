package com.colofabrix.scala.beesight

import cats.effect.{ ExitCode, IO, IOApp, Sync }
import cats.effect.std.Console
import cats.effect.std.Console
import cats.Functor
import cats.syntax.all._
import com.monovore.decline._

trait IODeclineApp[A] extends IOApp with DeclineApp[IO, A]:

  final override def run(args: List[String]): IO[ExitCode] =
    runDeclineApp(args)

trait DeclineApp[F[_]: Sync: Console: Functor, A]:

  def name: String
  def options: Opts[A]
  def header: String
  def runWithConfig(config: A): F[ExitCode]

  def helpFlag: Boolean = true
  def version: String   = ""

  def runDeclineApp(args: List[String]): F[ExitCode] =
    val ranOptions = addVersionFlag(options.map(runWithConfig))
    val command    = Command(name, header, helpFlag)(ranOptions)
    val feedArgs   = PlatformApp.ambientArgs getOrElse args
    val feedEnv    = PlatformApp.ambientEnvs getOrElse sys.env

    for {
      parseResult <- Sync[F].delay(command.parse(feedArgs, feedEnv))
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
          .flag(
            long = "version",
            short = "v",
            help = "Print the version number and exit.",
            visibility = Visibility.Partial,
          )
          .as {
            Console[F].println(nonNullVersion).as(ExitCode.Success)
          }
          .orElse {
            opts
          }
      }
      .getOrElse {
        opts
      }
