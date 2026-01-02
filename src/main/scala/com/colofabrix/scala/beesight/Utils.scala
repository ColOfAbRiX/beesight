package com.colofabrix.scala.beesight

import cats.effect.IO
import cats.Show
import fs2.Stream

/**
 * Utilities for fs2.Stream
 */
object StreamUtils {

  def fs2Io[A](f: => A): Stream[IO, A] =
    Stream.eval(IO(f))

  def fs2Println[A: cats.Show](a: => A): Stream[IO, Unit] =
    Stream.eval(IO.println(a))

  extension [A](self: Stream[IO, A]) {
    def ioTap[B](f: A => B): Stream[IO, A] =
      self.evalTap(a => IO(f(a)))

    def ioTapPrintln[B: cats.Show](f: A => B): Stream[IO, A] =
      self.evalTap(a => IO.println(f(a)))
  }

}
