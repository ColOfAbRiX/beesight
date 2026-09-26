package com.colofabrix.scala.beesight.config

import cats.Show
import cats.syntax.contravariant.*
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.*

/**
 * Size of a sliding window used during detection (must be ≥ 1).
 */
opaque type WindowSize = Int :| GreaterEqual[1]

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

/**
 * A speed value in m/s (climb/descent rate or horizontal speed).
 */
opaque type Speed = Double

object Speed {

  def apply(value: Double): Speed =
    value

  extension (self: Speed)
    def value: Double = self

  given Ordering[Speed] =
    Ordering.by(_.value)

  given Show[Speed] =
    Show[Double].contramap[Speed](_.value)

}

/**
 * An altitude value in metres (AGL or MSL depending on context).
 */
opaque type Altitude = Double

object Altitude {

  def apply(value: Double): Altitude =
    value

  extension (self: Altitude)
    def value: Double = self

  given Ordering[Altitude] =
    Ordering.by(_.value)

  given Show[Altitude] =
    Show[Double].contramap[Altitude](_.value)

}

/**
 * An acceleration value in m/s².
 */
opaque type Acceleration = Double

object Acceleration {

  def apply(value: Double): Acceleration =
    value

  extension (self: Acceleration)
    def value: Double = self

  given Ordering[Acceleration] =
    Ordering.by(_.value)

  given Show[Acceleration] =
    Show[Double].contramap[Acceleration](_.value)

}

/**
 * A mass value in kg (total flying weight of pilot and equipment).
 */
opaque type Mass = Double

object Mass {

  def apply(value: Double): Mass =
    value

  extension (self: Mass)
    def value: Double = self

  given Ordering[Mass] =
    Ordering.by(_.value)

  given Show[Mass] =
    Show[Double].contramap[Mass](_.value)

}

/**
 * An area value in m² (canopy planform area).
 */
opaque type Area = Double

object Area {

  def apply(value: Double): Area =
    value

  extension (self: Area)
    def value: Double = self

  given Ordering[Area] =
    Ordering.by(_.value)

  given Show[Area] =
    Show[Double].contramap[Area](_.value)

}
