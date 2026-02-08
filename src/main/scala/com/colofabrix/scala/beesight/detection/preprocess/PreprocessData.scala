package com.colofabrix.scala.beesight.detection.preprocess

import cats.data.State
import cats.syntax.all.*
import com.colofabrix.scala.beesight.collections.*
import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.detection.math.*
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.utils.*
import java.time.Instant
import monocle.syntax.all.*

final private[detection] class PreprocessData[A] private (config: DetectionConfig) {

  private type StreamState[A] = State[PreprocessState, A]

  def preprocess[F[_]]: fs2.Pipe[F, DataPoint, DataPoint] =
    _.mapStateCollect(initialPreprocessState) { currentPoint =>
      for {
        currentData           <- buildCurrentWindowData(currentPoint)
        _                     <- despikeSecondOldest()
        oldestData            <- pushWindowData(currentData)
        result                 = oldestData.map(_.point)
      } yield result
    }

  private val initialPreprocessState: PreprocessState =
    PreprocessState(SlidingWindow(Math.max(2, config.global.preprocessWindowSize)))

  private def buildCurrentWindowData(currentPoint: DataPoint): StreamState[SpikeWindowData] =
    State.inspect {
      case PreprocessState(window) =>
        val kinematics =
          window
            .sliceFilled(window.length - 1, 1)
            .map { previousData =>
              Kinematics.compute(previousData.focus.point, currentPoint)
            }

        SpikeWindowData(currentPoint, kinematics)
    }

  private def pushWindowData(currentData: SpikeWindowData): StreamState[Option[SpikeWindowData]] =
    State {
      case PreprocessState(currentWindow) =>
        val (oldestData, nextWindow) = currentWindow.push(currentData)
        val nextState                = PreprocessState(nextWindow)
        (nextState, oldestData)
    }

  private def despikeSecondOldest(): StreamState[Unit] =
    State.modify {
      case currentState @ PreprocessState(currentWindow: SlidingWindow.FilledWindow[SpikeWindowData]) =>
        currentState
          .focus(_.window)
          .replace(Despike.despike(config, currentWindow))
      case currentState @ PreprocessState(_) =>
        currentState
    }

}

object PreprocessData {

  def apply[A](config: DetectionConfig): PreprocessData[A] =
    new PreprocessData[A](config)

}
