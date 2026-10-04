package com.colofabrix.scala.beesight.math

import java.time.Instant

/**
 * Algebraic linear blend
 */
private[beesight] trait Lerp[A] {

  def lerp(a: A, b: A, t1: Instant, t2: Instant, t: Instant): A

}

private[beesight] object Lerp {

  def apply[A: Lerp]: Lerp[A] =
    summon

  given Lerp[Double] with
    def lerp(a: Double, b: Double, t1: Instant, t2: Instant, t: Instant): Double =
      a + (b - a) * tParam(t1, t2, t)

  def tParam(t1: Instant, t2: Instant, time: Instant): Double =
    ((time.toEpochMilli - t1.toEpochMilli).toDouble / (t2.toEpochMilli - t1.toEpochMilli))

}

/**
 * Algebraic linear interpolation
 */
private[beesight] trait Interpolation[A] {

  def interpolate(a: A, b: A, time: Instant): A

}

private[beesight] object Interpolation {

  def apply[A: Interpolation]: Interpolation[A] =
    summon

}

extension [A: Interpolation](self: A) {
  def interpolate(x2: A)(time: Instant): A =
    Interpolation[A].interpolate(self, x2, time)
}

extension [A: Lerp](self: A) {
  def interpolate(x2: A)(t1: Instant, t2: Instant, time: Instant): A =
    Lerp[A].lerp(self, x2, t1, t2, time)
}
