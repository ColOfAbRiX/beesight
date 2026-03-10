package com.colofabrix.scala.beesight.config

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.*

opaque type ProcessLimit = Int :| GreaterEqual[1]

object ProcessLimit {

  def apply(value: Int :| GreaterEqual[1]): ProcessLimit =
    value

  extension (self: ProcessLimit)
    def value: Int :| GreaterEqual[1] = self

  given Ordering[ProcessLimit] =
    Ordering.by(_.value)

}

opaque type Parallelism = Int :| GreaterEqual[1]

object Parallelism {

  def apply(value: Int :| GreaterEqual[1]): Parallelism =
    value

  extension (self: Parallelism)
    def value: Int :| GreaterEqual[1] = self

  given Ordering[Parallelism] =
    Ordering.by(_.value)

}

opaque type WindowSize = Int :| GreaterEqual[1]

object WindowSize {

  def apply(value: Int :| GreaterEqual[1]): WindowSize =
    value

  extension (self: WindowSize)
    def value: Int :| GreaterEqual[1] = self

  given Ordering[WindowSize] =
    Ordering.by(_.value)

}

opaque type Percent = Double :| Interval.Closed[0.0, 1.0]

object Percent {

  def apply(value: Double :| Interval.Closed[0.0, 1.0]): Percent =
    value

  extension (self: Percent)
    def value: Double :| Interval.Closed[0.0, 1.0] = self

  given Ordering[Percent] =
    Ordering.by(_.value)

}

opaque type KeepPoints = Int :| GreaterEqual[0]

object KeepPoints {

  def apply(value: Int :| GreaterEqual[0]): KeepPoints =
    value

  extension (self: KeepPoints)
    def value: Int :| GreaterEqual[0] = self

  given Ordering[KeepPoints] =
    Ordering.by(_.value)

}
