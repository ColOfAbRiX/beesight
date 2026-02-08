package com.colofabrix.scala.beesight.model.formats

import com.colofabrix.scala.beesight.detection.model.DataPoint
import com.colofabrix.scala.beesight.model.OutputFlightPoint

/**
 * Type class for adapting various flight data file formats to a common InputFlightPoint representation.
 */
trait FileFormatAdapter[A] {

  /**
   * Converts a source data point to an DataPoint.
   */
  def toDataPoint(a: A): DataPoint

}

object FileFormatAdapter {

  /**
   * Summons the FileFormatAdapter instance for type A.
   */
  def apply[A](using ev: FileFormatAdapter[A]): FileFormatAdapter[A] = ev

}
