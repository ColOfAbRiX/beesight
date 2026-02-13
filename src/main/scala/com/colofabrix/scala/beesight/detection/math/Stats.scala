package com.colofabrix.scala.beesight.detection.math

import breeze.linalg.DenseVector
import breeze.stats.median as breezeMedian
import cats.data.NonEmptyVector
import cats.syntax.all.*
import com.colofabrix.scala.beesight.collections.NEW
import com.colofabrix.scala.beesight.model.GeoVector

object Stats {

  def median(xs: IterableOnce[Double]): Double =
    val iter = xs.iterator
    val arr  = iter.toArray
    if arr.isEmpty then 0.0 else breezeMedian(DenseVector(arr))

  def median(window: NEW[GeoVector]): GeoVector =
    val (east, north, vertical) =
      window.foldMap { vector =>
        (List(vector.east), List(vector.north), List(vector.vertical))
      }

    GeoVector(
      north = median(north),
      east = median(east),
      vertical = median(vertical),
    )

}
