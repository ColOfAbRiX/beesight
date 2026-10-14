package com.colofabrix.scala.beesight.detection.model

import com.colofabrix.scala.beesight.detection.config.Angle
import scala.math.Ordering.Implicits.*

/**
 * The direction of the swoop turn, as seen from above: the pilot builds speed by turning left or right.
 */
enum TurnDirection {
  case Left
  case Right
}

object TurnDirection {

  /**
   * Resolves the turn direction from a signed rotation angle: negative degrees turn left, positive or zero turn
   * right.
   */
  def fromRotation(degrees: Angle): TurnDirection =
    if degrees < Angle(0.0) then
      TurnDirection.Left
    else
      TurnDirection.Right

}
