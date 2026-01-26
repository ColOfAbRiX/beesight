package com.colofabrix.scala.beesight.detection

import com.colofabrix.scala.beesight.config.GlobalConfig
import com.colofabrix.scala.beesight.detection.model.PointKinematics
import com.colofabrix.scala.beesight.model.InputFlightRow
import java.time.Duration

object Preprocessing {

  // TODO: This function should be in its own file Kinematics.scala
  def computeKinematics[A](
    current: InputFlightRow[A],
    previous: Option[InputFlightRow[A]],
    previousKinematics: Option[PointKinematics],
    config: GlobalConfig,
  ): PointKinematics = {
    val deltaTime =
      previous.fold(0.2) { prev =>
        Duration.between(prev.time, current.time).toMillis / 1000.0
      }

    val (clippedVert, clippedNorth, clippedEast) =
      previousKinematics match {
        case None =>
          (current.verticalSpeed, current.northSpeed, current.eastSpeed)
        case Some(prevK) =>
          (
            clipSpeed(current.verticalSpeed, prevK.clippedVerticalSpeed, deltaTime, config.accelerationClip),
            clipSpeed(current.northSpeed, prevK.clippedNorthSpeed, deltaTime, config.accelerationClip),
            clipSpeed(current.eastSpeed, prevK.clippedEastSpeed, deltaTime, config.accelerationClip),
          )
      }

    val correctedAltitude =
      previousKinematics.fold(current.altitude) { prevK =>
        if (clippedVert != current.verticalSpeed)
          prevK.correctedAltitude - clippedVert * deltaTime
        else
          current.altitude
      }

    val horizontalSpeed = math.sqrt(clippedNorth * clippedNorth + clippedEast * clippedEast)
    val totalSpeed      = math.sqrt(horizontalSpeed * horizontalSpeed + clippedVert * clippedVert)

    PointKinematics(
      rawVerticalSpeed = current.verticalSpeed,
      rawNorthSpeed = current.northSpeed,
      rawEastSpeed = current.eastSpeed,
      clippedVerticalSpeed = clippedVert,
      clippedNorthSpeed = clippedNorth,
      clippedEastSpeed = clippedEast,
      correctedAltitude = correctedAltitude,
      horizontalSpeed = horizontalSpeed,
      totalSpeed = totalSpeed,
      deltaTime = deltaTime,
    )
  }

  private def clipSpeed(current: Double, previous: Double, deltaTime: Double, maxAcceleration: Double): Double = {
    val delta    = current - previous
    val maxDelta = maxAcceleration * deltaTime

    if (math.abs(delta) > maxDelta)
      previous + math.signum(delta) * maxDelta
    else
      current
  }

}
