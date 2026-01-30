package com.colofabrix.scala.beesight

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.scalatest.exceptions.TestFailedException
import org.scalatest.Suite
import scala.concurrent.duration.*

/**
 * Mixin trait that provides helper methods for scalatest Suite, similar to scalatest's EitherValues, to test IO and
 * access its values, including exceptions
 */
trait IOValues {
  self: Suite =>

  private given testRuntime: IORuntime =
    cats.effect.unsafe.implicits.global

  extension [A](self: IO[A]) {

    /** The success value contained in the monad */
    def result(timeout: FiniteDuration = 30.seconds): A =
      self
        .unsafeRunTimed(timeout)(using IORuntime.global)
        .getOrElse {
          fail("Timeout while waiting for operation to complete")
        }

    /** True if the monad contains an exception */
    def isException(timeout: FiniteDuration = 30.seconds): Boolean =
      self
        .redeem(_ => true, _ => false)
        .unsafeRunTimed(timeout)
        .getOrElse {
          fail("Timeout while waiting for operation to complete")
        }

    /** The exception value contained in the monad */
    def exception(timeout: FiniteDuration = 30.seconds): Throwable =
      self
        .redeemWith(
          error => IO(error),
          _ => IO.raiseError(new TestFailedException(Some("The IO value did not contain an exception."), None, 1)),
        )
        .unsafeRunTimed(timeout)
        .getOrElse {
          fail("Timeout while waiting for operation to complete")
        }

  }

}
