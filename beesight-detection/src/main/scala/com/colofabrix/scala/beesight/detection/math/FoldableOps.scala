package com.colofabrix.scala.beesight.detection.math

import cats.Foldable
import cats.syntax.all.*
import scala.reflect.ClassTag

extension [F[_]: Foldable, A](fa: F[A]) {

  /**
   * Single-pass, pre-allocated extraction of a Foldable into a primitive-friendly Array.
   */
  def toArrayBy[B: ClassTag](f: A => B): Array[B] =
    val n   = fa.size.toInt
    val arr = new Array[B](n)
    var i   = 0
    fa.foldLeft(())((_, a) => { arr(i) = f(a); i += 1 })
    arr

}
