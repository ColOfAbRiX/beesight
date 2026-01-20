package com.colofabrix.scala.declinio

import cats.data.ReaderT
import cats.effect.*
import cats.effect.std.Console
import cats.implicits.given
import cats.~>
import com.monovore.decline.*

/**
 * Decline application for any effect F[_] and configuration A that uses a ReaderT to pass the configuration
 */
transparent trait DeclineReaderApp[F[_], A] extends IOApp:

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
   * If set to true, displays a help message when the user inputs wrong arguments
   */
  def helpFlag: Boolean = true

  /**
   * Decline command line options
   */
  def options: Opts[A]

  /**
   * Application main method that uses a ReaderT to pass the compiled configuration
   */
  def runWithReader: ReaderT[F, A, ExitCode]

  /**
   * Executor that transforms the application effect F[_] into IO[_]
   */
  protected def runEffectToIO: F ~> IO

  private def runDeclineApp(args: List[String]): IO[ExitCode] =
    val mainOpts =
      options.map { config =>
        runEffectToIO(runWithReader.run(config))
      }

    val mainVersionOpts = addVersionFlag(mainOpts)
    val mainCommand     = Command(name, header, helpFlag)(mainVersionOpts)
    val feedArgs        = PlatformApp.ambientArgs.getOrElse(args)
    val feedEnv         = PlatformApp.ambientEnvs.getOrElse(sys.env)

    for {
      parseResult <- IO.delay(mainCommand.parse(feedArgs, feedEnv))
      exitCode    <- parseResult.fold(printHelp, identity)
    } yield exitCode

  final override def run(args: List[String]): IO[ExitCode] =
    runDeclineApp(args)

  private def printHelp(help: Help): IO[ExitCode] =
    Console[IO].errorln(help).as {
      if (help.errors.nonEmpty) ExitCode.Error
      else ExitCode.Success
    }

  private def addVersionFlag(opts: Opts[IO[ExitCode]]): Opts[IO[ExitCode]] =
    Option(version)
      .filter(_.nonEmpty)
      .map { nonNullVersion =>
        Opts
          .flag("version", "Print the version number and exit.", "v", Visibility.Partial)
          .as(Console[IO].println(nonNullVersion).as(ExitCode.Success))
          .orElse(opts)
      }
      .getOrElse(opts)
