package com.colofabrix.scala.beesight

import cats.effect.IO
import cats.Show
import fs2.Stream

object StreamUtils:

  extension (self: Stream.type)
    def io[A](f: => A): Stream[IO, A]                      = Stream.eval(IO(f))
    def ioPrintln[A: cats.Show](a: => A): Stream[IO, Unit] = Stream.eval(IO.println(a))

  extension [A](self: Stream[IO, A])
    def ioTap[B](f: A => B): Stream[IO, A]                   = self.evalTap(a => IO(f(a)))
    def ioTapPrintln[B: cats.Show](f: A => B): Stream[IO, A] = self.evalTap(a => IO.println(f(a)))

object FileUtils:

  given Show[better.files.File] =
    _.toString

  given fs2ToBf: Conversion[fs2.io.file.Path, better.files.File] =
    path => better.files.File(path.toNioPath)

  given bfToFs2: Conversion[better.files.File, fs2.io.file.Path] =
    path => fs2.io.file.Path.fromNioPath(path.path)
