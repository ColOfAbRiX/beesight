package com.colofabrix.scala.beesight.detection.readers

import com.colofabrix.scala.beesight.detection.model.GeoVector
import java.time.Instant

/**
 * Reader for a point of flight data
 */
trait FlysightReader[A] extends PointReader[A] with GpsReader[A] {
  def lat(point: A): Double
  def lon(point: A): Double
  def heading(point: A): Double
  def gpsFix(point: A): Option[Int]
}

object FlysightReader {

  def apply[A: FlysightReader]: FlysightReader[A] =
    summon

}
