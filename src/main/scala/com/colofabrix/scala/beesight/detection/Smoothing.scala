package com.colofabrix.scala.beesight.detection

import breeze.linalg.DenseVector
import breeze.stats.{ mean as breezeMean, median as breezeMedian, stddev as breezeStddev }
import com.colofabrix.scala.beesight.collections.FixedSizeQueue

object Smoothing {

  def median(queue: FixedSizeQueue[Double]): Double = {
    val arr = queue.toArray
    if (arr.isEmpty) 0.0 else breezeMedian(DenseVector(arr))
  }

  def mean(queue: FixedSizeQueue[Double]): Double = {
    val arr = queue.toArray
    if (arr.isEmpty) 0.0 else breezeMean(DenseVector(arr))
  }

  def stdDev(queue: FixedSizeQueue[Double]): Double = {
    val arr = queue.toArray
    if (arr.length < 2) 0.0 else breezeStddev(DenseVector(arr))
  }

  def computeAcceleration(currentSmoothed: Double, previousSmoothed: Double, deltaTime: Double): Double =
    if (deltaTime <= 0) 0.0
    else (currentSmoothed - previousSmoothed) / deltaTime

}
