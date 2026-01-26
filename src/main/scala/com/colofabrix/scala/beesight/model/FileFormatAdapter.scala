package com.colofabrix.scala.beesight.model

trait FileFormatAdapter[A] {

  def toInputFlightPoint(a: A): InputFlightRow[A]

  final def altitude(a: A): Double =
    toInputFlightPoint(a).altitude

  final def verticalSpeed(a: A): Double =
    toInputFlightPoint(a).verticalSpeed

}

object FileFormatAdapter {

  def apply[A](using ev: FileFormatAdapter[A]): FileFormatAdapter[A] = ev

}
