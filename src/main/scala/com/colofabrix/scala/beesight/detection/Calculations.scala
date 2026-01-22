package com.colofabrix.scala.beesight.detection

import com.colofabrix.scala.beesight.model.{ FlightPhase, FlightPoint }
import com.colofabrix.scala.beesight.detection.model.VerticalSpeedSample

/**
 * Shared calculation utilities used by multiple detectors.
 */
private[detection] object Calculations {

  /**
   * Find the inflection point in the backtrack window where vertical speed started changing.
   */
  def findInflectionPoint(
    history: Vector[VerticalSpeedSample],
    detectedPoint: FlightPoint,
    isRising: Boolean,
  ): FlightPoint =
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

      FlightPoint(candidate.index, candidate.altitude)

}
