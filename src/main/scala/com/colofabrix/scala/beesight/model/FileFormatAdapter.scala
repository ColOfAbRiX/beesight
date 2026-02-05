package com.colofabrix.scala.beesight.model

/**
 * Type class for adapting various flight data file formats to a common InputFlightRow representation.
 */
trait FileFormatAdapter[A] {

  /**
   * Converts a source data point to an InputFlightRow.
   */
  def toInputFlightPoint(a: A): InputFlightRow[A]

  /**
   * Extracts the altitude from a source data point.
   */
  final def altitude(a: A): Double =
    toInputFlightPoint(a).altitude

  /**
   * Extracts the vertical speed from a source data point.
   */
  final def verticalSpeed(a: A): Double =
    toInputFlightPoint(a).speed.vertical

}

object FileFormatAdapter {

  /**
   * Summons the FileFormatAdapter instance for type A.
   */
  def apply[A](using ev: FileFormatAdapter[A]): FileFormatAdapter[A] = ev

}
