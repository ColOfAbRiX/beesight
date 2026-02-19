package com.colofabrix.scala.beesight.detection.preprocess

import cats.data.State
import cats.syntax.all.*
import com.colofabrix.scala.beesight.collections.*
import com.colofabrix.scala.beesight.collections.NonEmptyWindow.*
import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.detection.math.*
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.utils.*
import monocle.syntax.all.*

final private[detection] class PreprocessData private (config: DetectionConfig) {

  private type StreamState[A] = State[PreprocessState, A]

  private val windowCapacity: Int =
    Math.max(2, config.global.preprocessWindowSize)

  def preprocess[F[_]]: fs2.Pipe[F, IndexedDataPoint, IndexedDataPoint] =
    _.stateMapEmits(initialPreprocessState) { currentPoint =>
      for {
        currentData <- buildCurrentWindowData(currentPoint)
        _           <- despikeSecondOldest()
        oldestData  <- pushWindowData(currentData)
      } yield oldestData.map(_.point)
    }

  private val initialPreprocessState: IndexedDataPoint => (PreprocessState, Option[IndexedDataPoint]) =
    firstPoint =>
      val windowData = SpikeWindowData(firstPoint, None)
      val window     = NonEmptyWindow(windowCapacity, windowData)
      PreprocessState(Some(window)) -> None

  private def buildCurrentWindowData(currentPoint: IndexedDataPoint): StreamState[SpikeWindowData] =
    State.inspect { state =>
      val kinematics =
        state
          .window
          .map { window =>
            Kinematics.compute(window.newest.point, currentPoint)
          }

      SpikeWindowData(currentPoint, kinematics)
    }

  private def pushWindowData(currentData: SpikeWindowData): StreamState[Option[SpikeWindowData]] =
    State { state =>
      state.window match {
        case None =>
          val newWindow = NonEmptyWindow(windowCapacity, currentData)
          (PreprocessState(Some(newWindow)), None)
        case Some(window) =>
          val (oldestData, newWindow) = window.push(currentData)
          (PreprocessState(Some(newWindow)), oldestData)
      }
    }

  private def despikeSecondOldest(): StreamState[Unit] =
    State.modify { state =>
      state.window match {
        case Some(filled: NonEmptyFilled[SpikeWindowData]) =>
          val despiked = Despike.despike(config, filled)
          state.focus(_.window).replace(Some(NonEmptyWindow.fromFilled(despiked)))
        case _ =>
          state
      }
    }

}

object PreprocessData {

  def apply(config: DetectionConfig): PreprocessData =
    new PreprocessData(config)

}
