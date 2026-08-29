package com.colofabrix.scala.beesight.model.formats

import com.colofabrix.scala.beesight.model.DataPoint

/**
 * Type class for adapting various flight data file formats to a common InputFlightPoint representation.
 */
private[beesight] trait FileFormatAdapter[A] {

  /**
   * Converts a source data point to an DataPoint.
   */
  def toDataPoint(a: A): DataPoint

}

private[beesight] object FileFormatAdapter {

  /**
   * Summons the FileFormatAdapter instance for type A.
   */
  def apply[A](using ev: FileFormatAdapter[A]): FileFormatAdapter[A] = ev

}
