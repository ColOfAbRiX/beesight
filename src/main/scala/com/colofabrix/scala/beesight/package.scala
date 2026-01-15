package com.colofabrix.scala.beesight

import cats.data.ReaderT
import cats.effect.IO
import cats.effect.LiftIO
import cats.effect.std.Console
import com.colofabrix.scala.beesight.config.Config
import cats.MonadThrow

type IOConfig[A] = ReaderT[IO, Config, A]

val IOConfig: ReaderT.type = ReaderT

extension (self: ReaderT.type) {

  def unit: IOConfig[Unit] =
    ReaderT.liftF(IO.unit)

  def stdout[A](a: A): IOConfig[Unit] =
    ReaderT.liftF(Console[IO].println(a))

  def stderr[A](a: A): IOConfig[Unit] =
    ReaderT.liftF(Console[IO].errorln(a))

  def blocking[A](thunk: => A): IOConfig[A] =
    ReaderT.liftF(IO.blocking(thunk))

}

extension (self: String) {

  def stdout: IOConfig[Unit] =
    IOConfig.stdout(self)

  def stderr: IOConfig[Unit] =
    IOConfig.stderr(self)

}

given liftIoConfig: LiftIO[IOConfig] with
  override def liftIO[A](ioa: IO[A]): IOConfig[A] =
    ReaderT.liftF(ioa)

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
