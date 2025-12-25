package com.colofabrix.scala.declinio

import cats.effect.*
import cats.effect.std.Console
import cats.syntax.all.*
import com.monovore.decline.*


/**
 * Decline application for any effect F[_] that supports Sync and Console
 */
transparent trait DeclineApp[F[_]: Sync: Console, A]:

  /**
   * Name of the application
   */
  def name: String

  /**
   * Short description of the application
   */
  def header: String

  /**
   * Version of the application
   */
  def version: String = ""

  /**
   * Decline command line options
   */
  def options: Opts[A]

  /**
   * If set to true, displays a help message when the user inputs wrong arguments
   */
  def helpFlag: Boolean = true

  /**
   * Application main method that received the compiled configuration
   */
  def runWithConfig(config: A): F[ExitCode]

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
