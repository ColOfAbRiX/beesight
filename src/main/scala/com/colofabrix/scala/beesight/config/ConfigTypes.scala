package com.colofabrix.scala.beesight.config

import cats.Show
import cats.syntax.contravariant.*
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.*

/**
 * Maximum number of files to process in a single run (must be ≥ 1).
 */
opaque type ProcessLimit = Int :| GreaterEqual[1]

/**
 * Factory and type class instances for ProcessLimit.
 */
object ProcessLimit {

  def apply(value: Int :| GreaterEqual[1]): ProcessLimit =
    value

  extension (self: ProcessLimit)
    def value: Int :| GreaterEqual[1] = self

  given Ordering[ProcessLimit] =
    Ordering.by(_.value)

  given Show[ProcessLimit] =
    Show[Int].contramap[ProcessLimit](_.value)

}

/**
 * Number of concurrent files to process in parallel (must be ≥ 1).
 */
opaque type Parallelism = Int :| GreaterEqual[1]

/**
 * Factory and type class instances for Parallelism.
 */
object Parallelism {

  def apply(value: Int :| GreaterEqual[1]): Parallelism =
    value

  def unapply(value: Parallelism): Some[Int] =
    Some(value)

  extension (self: Parallelism)
    def value: Int :| GreaterEqual[1] = self

  given Ordering[Parallelism] =
    Ordering.by(_.value)

  given Show[Parallelism] =
    Show[Int].contramap[Parallelism](_.value)

}

/**
 * Size of a sliding window used during detection (must be ≥ 1).
 */
opaque type WindowSize = Int :| GreaterEqual[1]

/**
 * Factory and type class instances for WindowSize.
 */
object WindowSize {

  def apply(value: Int :| GreaterEqual[1]): WindowSize =
    value

  extension (self: WindowSize)
    def value: Int :| GreaterEqual[1] = self

  given Ordering[WindowSize] =
    Ordering.by(_.value)

  given Show[WindowSize] =
    Show[Int].contramap[WindowSize](_.value)

}

/**
 * A percentage value clamped to the range [0.0, 1.0].
 */
opaque type Percent = Double :| Interval.Closed[0.0, 1.0]

/**
 * Factory and type class instances for Percent.
 */
object Percent {

  def apply(value: Double :| Interval.Closed[0.0, 1.0]): Percent =
    value

  extension (self: Percent)
    def value: Double :| Interval.Closed[0.0, 1.0] = self

  given Ordering[Percent] =
    Ordering.by(_.value)

  given Show[Percent] =
    Show[Double].contramap[Percent](_.value)

}

/**
 * Number of data points to retain before takeoff or after landing (must be ≥ 0).
 */
opaque type KeepPoints = Int :| GreaterEqual[0]

/**
 * Factory and type class instances for KeepPoints.
 */
object KeepPoints {

  def apply(value: Int :| GreaterEqual[0]): KeepPoints =
    value

  extension (self: KeepPoints)
    def value: Int :| GreaterEqual[0] = self

  given Ordering[KeepPoints] =
    Ordering.by(_.value)

  given Show[KeepPoints] =
    Show[Int].contramap[KeepPoints](_.value)

}
