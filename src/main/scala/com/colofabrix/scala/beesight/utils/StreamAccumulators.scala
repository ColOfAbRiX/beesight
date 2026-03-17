package com.colofabrix.scala.beesight.utils

import cats.Foldable

/**
 * Provides stateful stream transformation with multi-element emission and drain support.
 */
private[utils] trait StreamAccumulators {

  extension [F[_], A](self: fs2.Stream[F, A]) {

    /**
     * Stateful stream transformation that can emit zero or more elements per input and drain remaining state at
     * stream end. The first element initialises the state via `init`; subsequent elements are processed by `f`.
     * When the upstream ends, `drain` is called to flush any buffered output.
     */
    def statefulMapEmits[G[_]: Foldable, SA, SB <: SA, B](
      init: A => (SA, G[B]),
      drain: SA => Vector[B] = (_: SA) => Vector.empty[B],
    )(f: (A, SA) => (SB, G[B]),
    ): fs2.Stream[F, B] =
      def loop(state: SA, stream: fs2.Stream[F, A]): fs2.Pull[F, B, Unit] =
        stream
          .pull
          .uncons1
          .flatMap {
            case Some((a, rest)) =>
              val (newState, output) = f(a, state)
              val items              = Foldable[G].toList(output)
              val chunk              = fs2.Chunk.from(items)
              fs2.Pull.output(chunk) >> loop(newState: SA, rest)
            case None =>
              fs2.Pull.output(fs2.Chunk.from(drain(state)))
          }

      self
        .pull
        .uncons1
        .flatMap {
          case Some((head, tail)) =>
            val (initState, initOutput) = init(head)
            val items                   = Foldable[G].toList(initOutput)
            val chunk                   = fs2.Chunk.from(items)
            fs2.Pull.output(chunk) >> loop(initState, tail)
          case None =>
            fs2.Pull.done
        }
        .stream

  }

}
