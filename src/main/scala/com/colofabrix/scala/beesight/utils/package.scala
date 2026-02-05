package com.colofabrix.scala.beesight

import cats.data.State

package object utils {

  extension [F[_], A](self: fs2.Stream[F, A]) {

    def mapStateCollect[S, B](init: S)(f: A => State[S, Option[B]]): fs2.Stream[F, B] =
      self
        .mapAccumulate(init)((s, a) => f(a).run(s).value)
        .collect { case (_, Some(output)) => output }

  }

}
