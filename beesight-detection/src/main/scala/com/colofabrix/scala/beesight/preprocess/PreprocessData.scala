package com.colofabrix.scala.beesight.preprocess

import cats.syntax.all.*
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
final class PreprocessData private (config: DetectionConfig) extends Preprocessor {

  private val windowCapacity: Int =
    Math.max(2, config.global.preprocessWindowSize.value)

  /**
   * Applies preprocessing to the data stream
   */
  def preprocess[F[_], A: PointReader]: fs2.Pipe[F, A, DataPoint] =
    _.statefulMapEmits(initialState, drainWindow) { (currentRow, window) =>
      val clipped =
        window match {
          case filled: NonEmptyFilled[PointKinematics] =>
            SpeedClipper.clip(config, filled)
          case other =>
            other
        }

      clipped
        .push {
          Kinematics.compute(window.newest.point, DataPoint.fromReader(currentRow))
        }
        .swap
        .bimap(identity, _.map(_.point))
    }

  private def initialState[A: PointReader](firstPoint: A): (NEW[PointKinematics], Option[DataPoint]) =
    val windowData    = PointKinematics(DataPoint.fromReader(firstPoint), GeoVector.zero)
    val initialWindow = NEW(windowCapacity, windowData)
    (initialWindow, None)

  private def drainWindow(window: NEW[PointKinematics]): Vector[DataPoint] =
    window
      .toVector
      .map(_.point)

}

object PreprocessData {

  def apply(config: DetectionConfig): PreprocessData =
    new PreprocessData(config)

}
