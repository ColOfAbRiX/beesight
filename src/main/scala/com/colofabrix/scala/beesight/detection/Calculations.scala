package com.colofabrix.scala.beesight.detection

import breeze.linalg.DenseVector
import breeze.stats.median
import com.colofabrix.scala.beesight.model.*

object Calculations {

  def computeFlightMetrics[A]( point: InputFlightPoint[?], verticalSpeedWindow: FixedSizeQueue[Double]): FlightMetrics =
    import point.*

    val horizontalSpeed = Math.sqrt(northSpeed * northSpeed + eastSpeed * eastSpeed)
    val totalSpeed      = Math.sqrt(northSpeed * northSpeed + eastSpeed * eastSpeed + verticalSpeed * verticalSpeed)

    val filteredVertSpeed =
      if verticalSpeedWindow.isEmpty then
        verticalSpeed
      else
        median(DenseVector((verticalSpeedWindow.toVector :+ verticalSpeed).toArray))

    FlightMetrics(
      altitude = altitude,
      vertSpeed = verticalSpeed,
      horizontalSpeed = horizontalSpeed,
      totalSpeed = totalSpeed,
      filteredVertSpeed = filteredVertSpeed,
    )

  def findInflectionPoint(
    history: Vector[VerticalSpeedSample],
    detectedPoint: FlightStagePoint,
    isRising: Boolean,
  ): FlightStagePoint =
    if history.isEmpty then
      detectedPoint
    else
      val candidate =
        history
          .sliding(2)
          .collect {
            case Vector(prev, curr) if isRising && curr.verticalSpeed > prev.verticalSpeed + 0.5  => prev
            case Vector(prev, curr) if !isRising && curr.verticalSpeed < prev.verticalSpeed - 0.5 => prev
          }
          .toList
          .headOption
          .getOrElse(if isRising then history.head else history.maxBy(_.verticalSpeed))

      FlightStagePoint(candidate.index, candidate.altitude)

}
