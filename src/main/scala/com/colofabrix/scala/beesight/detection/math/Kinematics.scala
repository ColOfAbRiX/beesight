package com.colofabrix.scala.beesight.detection.math

import com.colofabrix.scala.beesight.detection.model.*
import java.time.Duration

object Kinematics {

  def compute(prev: DataPoint, curr: DataPoint): PointKinematics =
    val prevSpeed    = GeoVector(prev.speed.north, prev.speed.east, prev.speed.vertical)
    val currSpeed    = GeoVector(curr.speed.north, curr.speed.east, curr.speed.vertical)
    val dt           = Duration.between(prev.time, curr.time).toMillis / 1000.0
    val acceleration = (currSpeed - prevSpeed) / dt

    PointKinematics(
      time = curr.time,
      altitude = curr.altitude,
      speed = currSpeed,
      acceleration = acceleration,
    )

}
