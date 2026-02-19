package com.colofabrix.scala.beesight.utils

import cats.Foldable
import cats.data.State

private[utils] trait StreamAccumulators {

  extension [F[_], A](self: fs2.Stream[F, A]) {

    def mapAccumulate1[S, B](init: A => (S, B))(f: (S, A) => (S, B)): fs2.Stream[F, (S, B)] =
      self
        .pull
        .uncons1
        .flatMap {
          case Some((head, tail)) =>
            val (initial, first) = init(head)
            val firstStream      = fs2.Stream.emit((initial, first))
            val tailStream       = tail.mapAccumulate(initial)(f)
            (firstStream ++ tailStream).pull.echo
          case None =>
            fs2.Pull.done
        }
        .stream

    def statefulMapEmits[G[_]: Foldable, SA, SB <: SA, B](
      init: A => (SA, G[B]),
    )(f: (A, SA) => (SB, G[B]),
    ): fs2.Stream[F, B] =
      self
        .mapAccumulate1[SA, G[B]](init) { (s, a) =>
          val (newState, output) = f(a, s)
          (newState: SA, output)
        }
        .flatMap {
          case (_, output) => fs2.Stream.emits(Foldable[G].toList(output))
        }

    def stateMapEmits[G[_]: Foldable, S, B](init: A => (S, G[B]))(f: A => State[S, G[B]]): fs2.Stream[F, B] =
      self.statefulMapEmits[G, S, S, B](init) { (a, s) =>
        f(a).run(s).value
      }

  }

}
