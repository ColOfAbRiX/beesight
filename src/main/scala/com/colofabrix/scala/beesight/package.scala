package com.colofabrix.scala.beesight

import cats.data.ReaderT
import cats.effect.IO
import com.colofabrix.scala.beesight.config.Config
import cats.effect.LiftIO

type IOConfig[A] = ReaderT[IO, Config, A]

val IOConfig: ReaderT.type = ReaderT

given liftIoConfig: LiftIO[IOConfig] with
  override def liftIO[A](ioa: IO[A]): IOConfig[A] =
    ReaderT.liftF(ioa)

extension (self: ReaderT.type) {

  def unit: IOConfig[Unit] =
    ReaderT.liftF(IO.unit)

  def println[A](a: A): IOConfig[Unit] =
    ReaderT.liftF(IO.println(a))

  def blocking[A](thunk: => A): IOConfig[A] =
    ReaderT.liftF(IO.blocking(thunk))

  def askConfig: IOConfig[Config] =
    ReaderT.ask[IO, Config]

  def mapConfig[A](f: Config => A): IOConfig[A] =
    ReaderT.ask[IO, Config].map(f)

  def flatMapConfig[A](f: Config => IOConfig[A]): IOConfig[A] =
    ReaderT.ask[IO, Config].flatMap(f)

}

