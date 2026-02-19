package com.colofabrix.scala.beesight.utils

import cats.data.IndexedState

private[utils] trait IndexedStateUtils {

  extension (self: IndexedState.type) {

    def get[S]: IndexedState[S, S, S] =
      IndexedState(s => (s, s))

    def modify[SA, SB](f: SA => SB): IndexedState[SA, SB, Unit] =
      IndexedState(s => (f(s), ()))

    def inspect[S, T](f: S => T): IndexedState[S, S, T] =
      IndexedState(s => (s, f(s)))

  }

}
