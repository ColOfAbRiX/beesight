package com.colofabrix.scala.beesight.model

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
  def fromRotation(degrees: Double): TurnDirection =
    if degrees < 0 then TurnDirection.Left 
    else TurnDirection.Right

}
