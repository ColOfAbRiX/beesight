package com.colofabrix.scala.beesight.detection.math

import com.colofabrix.scala.beesight.detection.model.GeoVector
import com.colofabrix.scala.beesight.detection.model.PointKinematics
import com.colofabrix.scala.beesight.model.InputFlightRow
import java.time.Duration

object Kinematics {

  def compute(prev: InputFlightRow[?], curr: InputFlightRow[?]): PointKinematics =
    val prevSpeed    = GeoVector(prev.northSpeed, prev.eastSpeed, prev.verticalSpeed)
    val currSpeed    = GeoVector(curr.northSpeed, curr.eastSpeed, curr.verticalSpeed)
    val dt           = Duration.between(prev.time, curr.time).toMillis / 1000.0
    val acceleration = (currSpeed - prevSpeed) / dt

    PointKinematics(
      time = curr.time,
      altitude = curr.altitude,
      speed = currSpeed,
      acceleration = acceleration,
    )

}
