package com.colofabrix.scala.beesight.detection.stages

import com.colofabrix.scala.beesight.collections.NonEmptyWindow.NonEmptyFilled
import com.colofabrix.scala.beesight.config.TakeoffConfig
import com.colofabrix.scala.beesight.detection.math.Stats
import com.colofabrix.scala.beesight.detection.model.{ DetectionData, IndexedDataPoint }
import com.colofabrix.scala.beesight.model.EventType
import java.time.Duration
import scala.annotation.nowarn

final class TakeoffHandler private (@nowarn config: TakeoffConfig) extends StageHandler {

  protected def detectTrigger(window: NonEmptyFilled[DetectionData]): Boolean =
    val kinematics = window.focus.kinematics

    val speedTrigger =
      kinematics.speed.vertical > config.verticalSpeedThreshold &&
      kinematics.speed.horizontal > config.horizontalSpeedThreshold

    val isAboveMinAltitude =
      kinematics.altitude > config.maxTakeoffAltitude

    println(s"  │  speedTrigger=${speedTrigger}, isAboveMinAltitude=${isAboveMinAltitude}")

    isAboveMinAltitude && speedTrigger

  protected def validateTrigger(window: NonEmptyFilled[DetectionData]): Boolean =
    val focus  = window.focus.kinematics
    val newest = window.newest.kinematics
    val dt     = Duration.between(focus.time, newest.time).toMillis / 1000.0

    val validationDistance = config.validationVerticalSpeed * dt

    val result = newest.altitude < focus.altitude - validationDistance
    println(s"  │  trigger validation=${result}")
    result

  protected def findInflection(window: NonEmptyFilled[DetectionData]): Option[Int] =
    Some(window.focusIndex)

}

object TakeoffHandler {

  def apply(config: TakeoffConfig): TakeoffHandler =
    new TakeoffHandler(config)

}
