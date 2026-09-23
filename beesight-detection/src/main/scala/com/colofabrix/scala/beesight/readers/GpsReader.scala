package com.colofabrix.scala.beesight.readers

/**
 * GPS quality data available for a data point. Adapters of formats that do not carry the data return `None`.
 */
trait GpsReader[A] {

  def horizontalAccuracy(point: A): Option[Double]
  def verticalAccuracy(point: A): Option[Double]
  def speedAccuracy(point: A): Option[Double]
  def courseAccuracy(point: A): Option[Double]
  def satellites(point: A): Option[Int]

}

object GpsReader {

  def apply[A: GpsReader]: GpsReader[A] =
    summon

}

extension [A: GpsReader](self: A) {

  def horizontalAccuracy: Option[Double] =
    GpsReader[A].horizontalAccuracy(self)

  def verticalAccuracy: Option[Double] =
    GpsReader[A].verticalAccuracy(self)

  def speedAccuracy: Option[Double] =
    GpsReader[A].speedAccuracy(self)

  def courseAccuracy: Option[Double] =
    GpsReader[A].courseAccuracy(self)

  def satellites: Option[Int] =
    GpsReader[A].satellites(self)

}
