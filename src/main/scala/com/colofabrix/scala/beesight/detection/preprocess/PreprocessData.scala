package com.colofabrix.scala.beesight.detection.preprocess

import cats.data.State
import cats.syntax.all.*
import com.colofabrix.scala.beesight.collections.*
import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.detection.math.*
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.model.InputFlightRow
import com.colofabrix.scala.beesight.utils.*
import java.time.Instant

final private[detection] class PreprocessData[A] private (config: DetectionConfig) {

  private type Point          = InputFlightRow[A]
  private type StreamState[A] = State[RollingState, A]

  final private case class WindowData(
    point: Point,
    kinematics: Option[PointKinematics],
  )

  final private case class RollingState(
    window: SlidingWindow[WindowData],
  )

  private object RollingState {
    val empty: RollingState =
      RollingState(SlidingWindow(Math.max(2, config.global.preprocessWindowSize)))
  }

  def preprocess[F[_]]: fs2.Pipe[F, Point, Point] =
    _.mapStateCollect(RollingState.empty) { currentPoint =>
      for {
        currentData <- buildCurrentWindowData(currentPoint)
        _           <- despikeSecondOldest()
        oldestData  <- pushWindowData(currentData)
        result       = oldestData.map(_.point)
      } yield result
    }

  private def buildCurrentWindowData(currentPoint: Point): StreamState[WindowData] =
    State.inspect {
      case RollingState(window) =>
        val kinematics =
          window
            .sliceFilled(window.length - 1, 1)
            .map { previousData =>
              Kinematics.compute(previousData.focus.point, currentPoint)
            }

        WindowData(currentPoint, kinematics)
    }

  private def pushWindowData(currentData: WindowData): StreamState[Option[WindowData]] =
    State {
      case RollingState(currentWindow) =>
        val (oldestData, nextWindow) = currentWindow.push(currentData)
        val nextState                = RollingState(nextWindow)
        (nextState, oldestData)
    }

  private def despikeSecondOldest(): StreamState[Unit] =
    State.modify {
      case currentState @ RollingState(currentWindow: SlidingWindow.FilledWindow[WindowData]) =>
        val despikedWindow =
          currentWindow
            .focusAt(1)
            .modifyFocus {
              case secondOldestData @ WindowData(_, None) =>
                secondOldestData
              case secondOldestData @ WindowData(_, Some(secondOldestKinematics)) =>
                if secondOldestKinematics.acceleration > config.global.accelerationClip then
                  interpolate(currentWindow.oldest, currentWindow.newest, secondOldestData.point.time)
                else
                  secondOldestData
            }

        currentState.copy(window = despikedWindow)

      case currentState @ RollingState(_) =>
        currentState
    }

  private def interpolate(w1: WindowData, w2: WindowData, time: Instant): WindowData = {
    val interpolatedKinematics =
      for {
        k1    <- w1.kinematics
        k2    <- w2.kinematics
        result = Interpolation.interpolate(k1, k2, time)
      } yield result

    WindowData(
      point = Interpolation.interpolate(w1.point, w2.point, time),
      kinematics = interpolatedKinematics,
    )
  }

}

object PreprocessData {

  def apply[A](config: DetectionConfig): PreprocessData[A] =
    new PreprocessData[A](config)

}
