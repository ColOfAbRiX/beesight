package com.colofabrix.scala.beesight.preprocess

import com.colofabrix.scala.beesight.collections.*
import com.colofabrix.scala.beesight.collections.NEW.*
import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.math.*
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.utils.*
import java.time.Instant

/**
 * Removes GPS data spikes and smooths velocity readings via a small sliding window.
 */
final class PreprocessData private (config: DetectionConfig) {

  private val windowCapacity: Int =
    Math.max(2, config.global.preprocessWindowSize.value)

  /**
   * Streaming pipe that preprocesses raw flight data points.
   */
  def preprocess[F[_], A: NormalizedPoint]: fs2.Pipe[F, A, NormalizedPoint[A]] =
    _.through(IndexedDataPoint.stream)
      .through(preprocessIndexed)
      .map(NormalizedPoint.extractAs[IndexedDataPoint, A])

  /**
   * Streaming pipe that preprocesses indexed flight data points.
   */
  private[beesight] def preprocessIndexed[F[_]]: fs2.Pipe[F, IndexedDataPoint, IndexedDataPoint] =
    _.statefulMapEmits(initialState, drainWindow) { (currentPoint, window) =>
      val kinematics  = Kinematics.compute(window.newest.point, currentPoint)
      val currentData = SpikeEntry(currentPoint, Some(kinematics))

      val despiked =
        window match {
          case filled: NonEmptyFilled[SpikeEntry] =>
            Despike.despike(config, filled)
          case other =>
            other
        }

      val (oldestData, newWindow) = despiked.push(currentData)

      (newWindow, oldestData.map(_.point))
    }

  private def initialState(firstPoint: IndexedDataPoint): (NEW[SpikeEntry], Option[IndexedDataPoint]) =
    val windowData    = SpikeEntry(firstPoint, None)
    val initialWindow = NEW(windowCapacity, windowData)
    (initialWindow, None)

  private def drainWindow(window: NEW[SpikeEntry]): Vector[IndexedDataPoint] =
    window
      .toVector
      .map(_.point)

}

object PreprocessData {

  def apply(config: DetectionConfig): PreprocessData =
    new PreprocessData(config)

}
