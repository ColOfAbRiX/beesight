package com.colofabrix.scala.beesight.formats.convert

import com.colofabrix.scala.beesight.detection.readers.FlysightReader
import com.colofabrix.scala.beesight.formats.devices.FlysightPoint
import java.time.{ OffsetDateTime, ZoneOffset }

extension [A](point: A)(using reader: FlysightReader[A]) {

  /**
   * Converts points of any supported format into the FlySight format.
   */
  def toFlySight(): FlysightPoint =
    val speed = reader.speed(point)
    FlysightPoint(
      time = OffsetDateTime.ofInstant(reader.time(point), ZoneOffset.UTC),
      lat = reader.lat(point),
      lon = reader.lon(point),
      hMSL = reader.altitude(point),
      velN = speed.north,
      velE = speed.east,
      velD = speed.vertical,
      hAcc = reader.horizontalAccuracy(point).getOrElse(0.0),
      vAcc = reader.verticalAccuracy(point).getOrElse(0.0),
      sAcc = reader.speedAccuracy(point).getOrElse(0.0),
      heading = reader.heading(point),
      cAcc = reader.courseAccuracy(point).getOrElse(0.0),
      gpsFix = reader.gpsFix(point),
      numSV = reader.satellites(point).getOrElse(0),
    )

}
