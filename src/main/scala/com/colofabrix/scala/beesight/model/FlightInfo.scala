package com.colofabrix.scala.beesight.model

trait FlightInfo[A] {

  def toInputFlightPoint(a: A): InputFlightPoint[A]

  final def altitude(a: A): Double =
    toInputFlightPoint(a).altitude

  final def verticalSpeed(a: A): Double =
    toInputFlightPoint(a).verticalSpeed

}
