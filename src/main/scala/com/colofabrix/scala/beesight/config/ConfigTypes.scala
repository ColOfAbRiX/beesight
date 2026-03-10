package com.colofabrix.scala.beesight.config

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.*

opaque type ProcessLimit = Int :| GreaterEqual[1]

object ProcessLimit {

  def apply(value: Int :| GreaterEqual[1]): ProcessLimit =
    value

  extension (self: ProcessLimit)
    def value: Int :| GreaterEqual[1] = self

  given Conversion[Int, ProcessLimit] with
    def apply(x: Int): ProcessLimit = x.refineUnsafe

}

opaque type Parallelism = Int :| GreaterEqual[1]

object Parallelism {

  def apply(value: Int :| GreaterEqual[1]): Parallelism =
    value

  extension (self: Parallelism)
    def value: Int :| GreaterEqual[1] = self

  given Conversion[Int, Parallelism] with
    def apply(x: Int): Parallelism = x.refineUnsafe

}

opaque type WindowSize = Int :| GreaterEqual[1]

object WindowSize {

  def apply(value: Int :| GreaterEqual[1]): WindowSize =
    value

  extension (self: WindowSize)
    def value: Int :| GreaterEqual[1] = self

  given Conversion[Int, WindowSize] with
    def apply(x: Int): WindowSize = x.refineUnsafe

}

opaque type Percent = Double :| Interval.Closed[0.0, 1.0]

object Percent {

  def apply(value: Double :| Interval.Closed[0.0, 1.0]): Percent =
    value

  extension (self: Percent)
    def value: Double :| Interval.Closed[0.0, 1.0] = self

  given Conversion[Double, Percent] with
    def apply(x: Double): Percent = x.refineUnsafe

}

opaque type KeepPoints = Int :| GreaterEqual[0]

object KeepPoints {

  def apply(value: Int :| GreaterEqual[0]): KeepPoints =
    value

  extension (self: KeepPoints)
    def value: Int :| GreaterEqual[0] = self

  given Conversion[Int, KeepPoints] with
    def apply(x: Int): KeepPoints = x.refineUnsafe

}
