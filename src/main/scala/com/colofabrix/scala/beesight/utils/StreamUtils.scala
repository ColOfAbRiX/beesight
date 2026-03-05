package com.colofabrix.scala.beesight.utils

private[utils] trait StreamUtils {

  extension [F[_], A](self: fs2.Stream[F, A]) {
    def drainWhen(cond: Boolean): fs2.Stream[F, Nothing] =
      if cond then self.drain else fs2.Stream.empty.drain
  }

}
