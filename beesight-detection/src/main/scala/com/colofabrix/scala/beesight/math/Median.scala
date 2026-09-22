package com.colofabrix.scala.beesight.math

import cats.Foldable
import cats.syntax.all.*
import com.colofabrix.scala.beesight.model.GeoVector
import scala.reflect.ClassTag
import scala.math.*

/**
 * How to compute the median of an array of A. The array may be mutated in place
 */
private[beesight] trait Median[A] {

  def median(values: Array[A]): A

}

private[beesight] trait LowPriorityMedian {

  given medianForFractional[A: Fractional]: Median[A] with {
    def median(values: Array[A]): A =
      val num    = summon[Fractional[A]]
      val sorted = values.sorted(using num)
      val n      = sorted.length

      if n % 2 == 0 then
        num.div(num.plus(sorted(n / 2 - 1), sorted(n / 2)), num.fromInt(2))
      else
        sorted(n / 2)
  }

}

private[beesight] object Median extends LowPriorityMedian {

  def apply[A: Median]: Median[A] =
    summon

  given Median[Double] with
    def median(values: Array[Double]): Double =
      val n = values.length
      if n <= 8 then
        insertionSort(values)
        if n % 2 == 0 then
          (values(n / 2 - 1) + values(n / 2)) / 2.0
        else
          values(n / 2)
      else
        val sorted = values.sorted
        if n % 2 == 0 then
          (sorted(n / 2 - 1) + sorted(n / 2)) / 2.0
        else
          sorted(n / 2)

  private def insertionSort(a: Array[Double]): Unit =
    var i = 1
    while i < a.length do
      val key = a(i)
      var j   = i - 1
      while j >= 0 && a(j) > key do
        a(j + 1) = a(j)
        j -= 1
      a(j + 1) = key
      i += 1

}
