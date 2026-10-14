package com.colofabrix.scala.beesight.formats.devices

import cats.data.NonEmptyList
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.detection.readers.*
import com.colofabrix.scala.beesight.formats.*
import fs2.data.csv.*
import java.time.*

/**
 * Dekunu Data Point, see https://github.com/ColOfAbRiX/dekunu-flysight-fix/blob/master/dekunu_to_flysight.py
 *
 * @param gpsTime          GPS time as Unix timestamp (seconds)
 * @param gpsLatitude      Latitude in micro-degrees (decimal degrees * 10^6)
 * @param gpsLongitude     Longitude in micro-degrees (decimal degrees * 10^6)
 * @param gpsAltitudeMeters Altitude above sea level (m)
 * @param gpsSpeedKnot     Ground speed (knots)
 * @param gpsAngleDegree   Course angle (degrees, 0 = North, 90 = East)
 * @param instVertSpeed    Instantaneous vertical speed (m/s), optional in the source file
 * @param gpsNumOfSats     Number of satellites used in the fix, optional in the source file
 */
final private[beesight] case class DekunuPoint(
  gpsTime: Long,
  gpsLatitude: Double,
  gpsLongitude: Double,
  gpsAltitudeMeters: Double,
  gpsSpeedKnot: Double,
  gpsAngleDegree: Double,
  instVertSpeed: Option[Double],
  gpsNumOfSats: Option[Int],
  extra: List[(String, String)],
)

private[beesight] object DekunuPoint {

  val dekunuSpecBuilder: CsvSpecBuilder[DekunuPoint] =
    CsvSpecBuilder.forFormat[DekunuPoint]

  import dekunuSpecBuilder._

  private val gpsTimeField =
    required[Long]("gpsTime", _.gpsTime.toString)
  private val gpsLatitudeField =
    required[Double]("gpsLatitude", p => formatDouble(p.gpsLatitude, 0))
  private val gpsLongitudeField =
    required[Double]("gpsLongitude", p => formatDouble(p.gpsLongitude, 0))
  private val gpsAltitudeField =
    required[Double]("gpsAltitudeMeters", p => formatDouble(p.gpsAltitudeMeters, 3))
  private val gpsSpeedField =
    required[Double]("gpsSpeedKnot", p => formatDouble(p.gpsSpeedKnot, 3))
  private val gpsAngleField =
    required[Double]("gpsAngleDegree", p => formatDouble(p.gpsAngleDegree, 5))
  private val instVertSpeedField =
    optional[Double]("instVertSpeedMetersPerSec", _.instVertSpeed.map(formatDouble(_, 3)).getOrElse(""))
  private val gpsNumOfSatsField =
    optional[Int]("gpsNumOfSats", _.gpsNumOfSats.map(_.toString).getOrElse(""))

  val fieldSpecs: NonEmptyList[CsvFieldSpec[DekunuPoint, ?]] =
    NonEmptyList.of(
      gpsTimeField,
      gpsLatitudeField,
      gpsLongitudeField,
      gpsAltitudeField,
      gpsSpeedField,
      gpsAngleField,
      instVertSpeedField,
      gpsNumOfSatsField,
    )

  val requiredColumns: Set[String] =
    fieldSpecs
      .toList
      .filter(_.required)
      .map(_.name)
      .toSet

  private val KnotsToMs = 0.51444444

  given dekunuPointReader: FlysightReader[DekunuPoint] with {
    def time(point: DekunuPoint): Instant =
      Instant.ofEpochSecond(point.gpsTime)
    def altitude(point: DekunuPoint): Double =
      point.gpsAltitudeMeters
    def speed(point: DekunuPoint): GeoVector =
      val speedMs    = point.gpsSpeedKnot * KnotsToMs
      val headingRad = Math.toRadians(point.gpsAngleDegree)
      GeoVector(
        north = speedMs * Math.cos(headingRad),
        east = speedMs * Math.sin(headingRad),
        vertical = point.instVertSpeed.getOrElse(0.0),
      )
    def lat(point: DekunuPoint): Double =
      point.gpsLatitude
    def lon(point: DekunuPoint): Double =
      point.gpsLongitude
    def heading(point: DekunuPoint): Double =
      point.gpsAngleDegree
    def gpsFix(point: DekunuPoint): Option[Int] =
      None
    def horizontalAccuracy(point: DekunuPoint): Option[Double] =
      None
    def verticalAccuracy(point: DekunuPoint): Option[Double] =
      None
    def speedAccuracy(point: DekunuPoint): Option[Double] =
      None
    def courseAccuracy(point: DekunuPoint): Option[Double] =
      None
    def satellites(point: DekunuPoint): Option[Int] =
      point.gpsNumOfSats
  }

  given csvRowDecoder: CsvRowDecoder[DekunuPoint, String] with {

    def apply(row: CsvRow[String]): DecoderResult[DekunuPoint] =
      for
        gpsTime       <- gpsTimeField.decode(row)
        gpsLatitude   <- gpsLatitudeField.decode(row)
        gpsLongitude  <- gpsLongitudeField.decode(row)
        gpsAltitude   <- gpsAltitudeField.decode(row)
        gpsSpeed      <- gpsSpeedField.decode(row)
        gpsAngle      <- gpsAngleField.decode(row)
        instVertSpeed <- instVertSpeedField.decode(row)
        gpsNumOfSats  <- gpsNumOfSatsField.decode(row)
      yield DekunuPoint(
        gpsTime = gpsTime,
        gpsLatitude = gpsLatitude,
        gpsLongitude = gpsLongitude,
        gpsAltitudeMeters = gpsAltitude,
        gpsSpeedKnot = gpsSpeed,
        gpsAngleDegree = gpsAngle,
        instVertSpeed = instVertSpeed,
        gpsNumOfSats = gpsNumOfSats,
        extra = List.empty,
      )

  }

  given csvRowEncoder: CsvRowEncoder[DekunuPoint, String] with {

    def apply(row: DekunuPoint): CsvRow[String] =
      CsvRow.fromNelHeaders(dekunuFields(row))

  }

  private def dekunuFields(row: DekunuPoint): NonEmptyList[(String, String)] =
    fieldSpecs
      .map(f => (f.name, f.encode(row)))
      .appendList(row.extra)

  private def formatDouble(value: Double, precision: Int): String =
    String.format(s"%.${precision}f", value)

}
