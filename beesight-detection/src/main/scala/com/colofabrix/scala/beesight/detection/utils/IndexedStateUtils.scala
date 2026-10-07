package com.colofabrix.scala.beesight.detection.utils

import cats.data.IndexedState

/**
 * Convenience constructors for Cats IndexedState.
 */
private[utils] trait IndexedStateUtils {

  extension (self: IndexedState.type) {

    /** Creates an IndexedState that transforms the state without producing a value. */
    def modify[SA, SB](f: SA => SB): IndexedState[SA, SB, Unit] =
      IndexedState(s => (f(s), ()))

  }

}
