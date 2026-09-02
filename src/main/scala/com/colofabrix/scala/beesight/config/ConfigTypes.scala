package com.colofabrix.scala.beesight.config

import cats.Show
import cats.syntax.contravariant.*
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.*

/**
 * Maximum number of files to process in a single run (must be ≥ 1).
 */
opaque type ProcessLimit = Int :| GreaterEqual[1]

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
