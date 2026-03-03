package com.colofabrix.scala.beesight.detection.stages

import cats.syntax.option.*
import com.colofabrix.scala.beesight.collections.NonEmptyWindow.NonEmptyFilled
import com.colofabrix.scala.beesight.config.TakeoffConfig
import com.colofabrix.scala.beesight.detection.DebugLog
import com.colofabrix.scala.beesight.detection.DebugLog.*
import com.colofabrix.scala.beesight.detection.math.Stats
import com.colofabrix.scala.beesight.detection.model.{ DetectionData, IndexedDataPoint }
import com.colofabrix.scala.beesight.model.EventType
import java.time.Duration

final class TakeoffHandler private (config: TakeoffConfig) extends StageHandler {

  protected def detectTrigger(window: NonEmptyFilled[DetectionData]): Boolean =
    val kinematics = window.focus.kinematics

    val speedTrigger =
      kinematics.speed.vertical < config.verticalSpeedThreshold &&
      kinematics.speed.horizontal > config.horizontalSpeedThreshold

    val isBelowMaxAltitude =
      kinematics.altitude < config.maxTakeoffAltitude

    DebugLog(s"  |  altitude=${kinematics.altitude.fmt}, verticalSpeed=${kinematics.speed.vertical.fmt}, horizontalSpeed=${kinematics.speed.horizontal.fmt}")
    DebugLog(s"  |  speedTrigger=$speedTrigger, isBelowMaxAltitude=$isBelowMaxAltitude")

    isBelowMaxAltitude && speedTrigger

  protected def validateTrigger(window: NonEmptyFilled[DetectionData]): Boolean =
    val focus  = window.focus.kinematics
    val newest = window.newest.kinematics
    val dt     = Duration.between(focus.time, newest.time).toMillis / 1000.0

    val validationDistance = config.validationVerticalSpeed * dt

    DebugLog(s"  |    altitude=${focus.altitude.fmt}, verticalSpeed=${focus.speed.vertical.fmt}, horizontalSpeed=${focus.speed.horizontal.fmt}")
    DebugLog(s"  |    validationDistance=${validationDistance.fmt}")

    newest.altitude > focus.altitude - validationDistance

  protected def findInflection(window: NonEmptyFilled[DetectionData]): Option[Int] =
    val discoveredInflection =
      for {
        backWindow  <- window.left
        speedsWindow = backWindow.map(_.kinematics.speed.vertical)
        inflection  <- Stats.findInflection(speedsWindow)
      } yield inflection

    discoveredInflection orElse window.focusIndex.some

}

object TakeoffHandler {

  def apply(config: TakeoffConfig): TakeoffHandler =
    new TakeoffHandler(config)

}
