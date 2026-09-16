package com.colofabrix.scala.beesight

import cats.MonadThrow
import cats.data.ReaderT
import cats.effect.std.Console
import cats.effect.{ IO, LiftIO }
import com.colofabrix.scala.beesight.config.Config

/**
 * Reader-based IO effect carrying application configuration through the call stack.
 */
type IOConfig[A] = ReaderT[IO, Config, A]

val IOConfig: ReaderT.type = ReaderT

/**
 * Convenience operations on the IOConfig companion for common effectful actions.
 */
extension (self: ReaderT.type) {

  /** Returns an IOConfig that does nothing. */
  def unit: IOConfig[Unit] =
    ReaderT.liftF(IO.unit)

  /** Prints a value to standard output within IOConfig. */
  def stdout[A](a: A): IOConfig[Unit] =
    ReaderT.liftF(Console[IO].println(a))

  /** Prints a value to standard error within IOConfig. */
  def stderr[A](a: A): IOConfig[Unit] =
    ReaderT.liftF(Console[IO].errorln(a))

  /** Lifts a blocking side-effecting thunk into IOConfig. */
  def blocking[A](thunk: => A): IOConfig[A] =
    ReaderT.liftF(IO.blocking(thunk))

  def fromOption[A](o: => Option[A])(orElse: => Throwable): IOConfig[A] =
    ReaderT.liftF(IO.fromOption(o)(orElse))

}

extension [A](self: Option[A]) {

  def toIOConfig(orElse: => Throwable): IOConfig[A] =
    ReaderT.liftF(IO.fromOption(self)(orElse))

}

extension [E <: Throwable, A](self: Either[E, A]) {

  def toIOConfig: IOConfig[A] =
    ReaderT.liftF(IO.fromEither(self))

}

/**
 * String convenience extensions for printing to stdout/stderr within IOConfig.
 */
extension (self: String) {

  /** Prints this string to standard output within IOConfig. */
  def stdout: IOConfig[Unit] =
    IOConfig.stdout(self)

  /** Prints this string to standard error within IOConfig. */
  def stderr: IOConfig[Unit] =
    IOConfig.stderr(self)

}

/**
 * LiftIO instance allowing plain IO values to be lifted into IOConfig.
 */
given liftIoConfig: LiftIO[IOConfig] with
  override def liftIO[A](ioa: IO[A]): IOConfig[A] =
    ReaderT.liftF(ioa)

/**
 * MonadThrow instance for IOConfig, enabling error handling and pure effect composition.
 */
given monadThrowIOConfig: MonadThrow[IOConfig] =
  new MonadThrow[IOConfig] {
    private val ioMT: MonadThrow[IO] = MonadThrow[IO]

    override def pure[A](x: A): IOConfig[A] =
      ReaderT.pure(x)

    override def flatMap[A, B](fa: IOConfig[A])(f: A => IOConfig[B]): IOConfig[B] =
      fa.flatMap(f)

    override def tailRecM[A, B](a: A)(f: A => IOConfig[Either[A, B]]): IOConfig[B] =
      ReaderT.catsDataMonadForKleisli[IO, Config].tailRecM(a)(f)

    override def raiseError[A](e: Throwable): IOConfig[A] =
      ReaderT.liftF(ioMT.raiseError(e))

    override def handleErrorWith[A](fa: IOConfig[A])(f: Throwable => IOConfig[A]): IOConfig[A] =
      ReaderT { config =>
        fa.run(config).handleErrorWith(e => f(e).run(config))
      }
  }
