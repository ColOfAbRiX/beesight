package com.colofabrix.scala.beesight

import cats.data.ReaderT
import cats.effect.IO
import cats.effect.LiftIO
import cats.effect.std.Console
import com.colofabrix.scala.beesight.config.Config

type IOConfig[A] = ReaderT[IO, Config, A]

val IOConfig: ReaderT.type = ReaderT

given liftIoConfig: LiftIO[IOConfig] with
  override def liftIO[A](ioa: IO[A]): IOConfig[A] =
    ReaderT.liftF(ioa)

extension (self: ReaderT.type) {

  def unit: IOConfig[Unit] =
    ReaderT.liftF(IO.unit)

  def println[A](a: A): IOConfig[Unit] =
    ReaderT.liftF(Console[IO].println(a))

  def errorln[A](a: A): IOConfig[Unit] =
    ReaderT.liftF(Console[IO].errorln(a))

  def blocking[A](thunk: => A): IOConfig[A] =
    ReaderT.liftF(IO.blocking(thunk))

}
