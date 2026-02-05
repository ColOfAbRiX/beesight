package com.colofabrix.scala.beesight.detection.math

import breeze.linalg.DenseVector
import breeze.stats.{ mean as breezeMean, median as breezeMedian, stddev as breezeStddev }
import com.colofabrix.scala.beesight.collections.FSQueue

object Smoothing {

  def median(queue: FSQueue[Double]): Double =
    if queue.isEmpty then 0.0
    else breezeMedian(queue.toDenseVector[Double])

  def mean(queue: FSQueue[Double]): Double =
    if queue.isEmpty then 0.0
    else breezeMean(queue.toDenseVector[Double])

  def stdDev(queue: FSQueue[Double]): Double =
    if queue.length < 2 then 0.0
    else breezeStddev(queue.toDenseVector[Double])

  def computeAcceleration(currentSmoothed: Double, previousSmoothed: Double, deltaTime: Double): Double =
    if (deltaTime <= 0) 0.0
    else (currentSmoothed - previousSmoothed) / deltaTime

}
