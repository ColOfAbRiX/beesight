package com.colofabrix.scala.beesight

import cats.data.State

package object utils {

  extension [F[_], A](self: fs2.Stream[F, A]) {

    def mapStateCollect[S, B](init: S)(f: A => State[S, Option[B]]): fs2.Stream[F, B] =
      self
        .mapAccumulate(init)((s, a) => f(a).run(s).value)
        .collect { case (_, Some(output)) => output }

    def mapStateCollect1[S, B](init: A => (S, Option[B]))(f: A => State[S, Option[B]]): fs2.Stream[F, B] =
      self
        .pull
        .uncons1
        .flatMap {
          case Some((head, tail)) =>
            val (initial, first) = init(head)
            val firstProcess     = first.fold(fs2.Stream.empty)(fs2.Stream.emit)
            val tailProcess      = tail.mapStateCollect(initial)(f)
            (firstProcess ++ tailProcess).pull.echo
          case None =>
            fs2.Pull.done
        }
        .stream

  }

}
