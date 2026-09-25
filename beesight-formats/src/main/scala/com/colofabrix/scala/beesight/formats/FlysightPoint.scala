package com.colofabrix.scala.beesight.formats

import cats.data.NonEmptyList
import com.colofabrix.scala.beesight.formats.derivation.given
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.readers.*
import fs2.data.csv.*
import java.time.*

/**
 * Flysight Data Point, see https://flysight.ca/wiki/index.php/File_format
 *
 * @param time Time in ISO8601 format
 * @param lat Latitude (degrees)
 * @param lon Longitude (degrees)
 * @param hMSL Height above sea level (m)
 * @param velN Velocity north (m/s)
 * @param velE Velocity east (m/s)
 * @param velD Velocity down (m/s)
 * @param hAcc Horizontal accuracy (m)
 * @param vAcc Vertical accuracy (m)
 * @param sAcc Speed accuracy (m/s)
 * @param heading Heading (degrees)
 * @param cAcc Course accuracy (degrees)
 * @param gpsFix GPS fix type (3 = 3D)
 * @param numSV Number of satellites used in fix
 */
final private[beesight] case class FlysightPoint(
  time: OffsetDateTime,
  lat: Double,
  lon: Double,
  hMSL: Double,
  velN: Double,
  velE: Double,
  velD: Double,
  hAcc: Double,
  vAcc: Double,
  sAcc: Double,
  heading: Double,
  cAcc: Double,
  gpsFix: Option[Int],
  numSV: Int,
  extra: List[(String, String)],
)

private[beesight] object FlysightPoint {

  val flysightSpecBuilder: CsvSpecBuilder[FlysightPoint] = CsvSpecBuilder.forFormat[FlysightPoint]

  import flysightSpecBuilder._

  private val timeField =
    required[OffsetDateTime]("time", _.time.toString)
  private val latField =
    required[Double]("lat", p => formatDouble(p.lat, 7))
  private val lonField =
    required[Double]("lon", p => formatDouble(p.lon, 7))
  private val hMSLField =
    required[Double]("hMSL", p => formatDouble(p.hMSL, 3))
  private val velNField =
    required[Double]("velN", p => formatDouble(p.velN, 2))
  private val velEField =
    required[Double]("velE", p => formatDouble(p.velE, 2))
  private val velDField =
    required[Double]("velD", p => formatDouble(p.velD, 2))
  private val hAccField =
    required[Double]("hAcc", p => formatDouble(p.hAcc, 3))
  private val vAccField =
    required[Double]("vAcc", p => formatDouble(p.vAcc, 3))
  private val sAccField =
    required[Double]("sAcc", p => formatDouble(p.sAcc, 2))
  private val headingField =
    required[Double]("heading", p => formatDouble(p.heading, 5))
  private val cAccField =
    required[Double]("cAcc", p => formatDouble(p.cAcc, 5))
  private val numSVField =
    required[Int]("numSV", _.numSV.toString)
  private val gpsFixField =
    optional[Int]("gpsFix", p => p.gpsFix.map(_.toString).getOrElse(""))

  val fieldSpecs: NonEmptyList[CsvFieldSpec[FlysightPoint, ?]] =
    NonEmptyList.of(
      timeField,
      latField,
      lonField,
      hMSLField,
      velNField,
      velEField,
      velDField,
      hAccField,
      vAccField,
      sAccField,
      headingField,
      cAccField,
      gpsFixField,
      numSVField,
    )

  val requiredColumns: Set[String] =
    fieldSpecs
      .toList
      .filter(_.required)
      .map(_.name)
      .toSet

  given flysightPointReader: PointReader[FlysightPoint] with {
    def time(point: FlysightPoint): Instant =
      point.time.toInstant
    def altitude(point: FlysightPoint): Double =
      point.hMSL
    def speed(point: FlysightPoint): GeoVector =
      GeoVector(north = point.velN, east = point.velE, vertical = point.velD)
  }

  given flysightGpsReader: GpsReader[FlysightPoint] with {
    def horizontalAccuracy(point: FlysightPoint): Option[Double] = Some(point.hAcc)
    def verticalAccuracy(point: FlysightPoint): Option[Double]   = Some(point.vAcc)
    def speedAccuracy(point: FlysightPoint): Option[Double]      = Some(point.sAcc)
    def courseAccuracy(point: FlysightPoint): Option[Double]     = Some(point.cAcc)
    def satellites(point: FlysightPoint): Option[Int]            = Some(point.numSV)
  }

  given csvRowDecoder: CsvRowDecoder[FlysightPoint, String] with {

    def apply(row: CsvRow[String]): DecoderResult[FlysightPoint] =
      for
        time    <- timeField.decode(row)
        lat     <- latField.decode(row)
        lon     <- lonField.decode(row)
        hMSL    <- hMSLField.decode(row)
        velN    <- velNField.decode(row)
        velE    <- velEField.decode(row)
        velD    <- velDField.decode(row)
        hAcc    <- hAccField.decode(row)
        vAcc    <- vAccField.decode(row)
        sAcc    <- sAccField.decode(row)
        heading <- headingField.decode(row)
        cAcc    <- cAccField.decode(row)
        gpsFix  <- gpsFixField.decode(row)
        numSV   <- numSVField.decode(row)
      yield FlysightPoint(
        time = time,
        lat = lat,
        lon = lon,
        hMSL = hMSL,
        velN = velN,
        velE = velE,
        velD = velD,
        hAcc = hAcc,
        vAcc = vAcc,
        sAcc = sAcc,
        heading = heading,
        cAcc = cAcc,
        gpsFix = gpsFix,
        numSV = numSV,
        extra = List.empty,
      )

  }

  given csvRowEncoder: CsvRowEncoder[FlysightPoint, String] with {

    def apply(row: FlysightPoint): CsvRow[String] =
      CsvRow.fromNelHeaders(flysightFields(row))

  }

  private def flysightFields(row: FlysightPoint): NonEmptyList[(String, String)] =
    fieldSpecs
      .map(f => (f.name, f.encode(row)))
      .appendList(row.extra)

  private def formatDouble(value: Double, precision: Int): String =
    String.format(s"%.${precision}f", value)

}
