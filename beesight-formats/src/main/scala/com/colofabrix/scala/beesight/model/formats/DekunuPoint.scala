package com.colofabrix.scala.beesight.model.formats

import cats.data.NonEmptyList
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.model.derivation.given
import fs2.data.csv.*
import java.time.*

/**
 * Dekunu Data Point, see https://github.com/ColOfAbRiX/dekunu-flysight-fix/blob/master/dekunu_to_flysight.py
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
final private[beesight] case class DekunuPoint(
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

private[beesight] object DekunuPoint {

  private val timeField =
    CsvFieldSpec.required[DekunuPoint, OffsetDateTime]("time", _.time.toString)
  private val latField =
    CsvFieldSpec.required[DekunuPoint, Double]("lat", p => formatDouble(p.lat, 7))
  private val lonField =
    CsvFieldSpec.required[DekunuPoint, Double]("lon", p => formatDouble(p.lon, 7))
  private val hMSLField =
    CsvFieldSpec.required[DekunuPoint, Double]("hMSL", p => formatDouble(p.hMSL, 3))
  private val velNField =
    CsvFieldSpec.required[DekunuPoint, Double]("velN", p => formatDouble(p.velN, 2))
  private val velEField =
    CsvFieldSpec.required[DekunuPoint, Double]("velE", p => formatDouble(p.velE, 2))
  private val velDField =
    CsvFieldSpec.required[DekunuPoint, Double]("velD", p => formatDouble(p.velD, 2))
  private val hAccField =
    CsvFieldSpec.required[DekunuPoint, Double]("hAcc", p => formatDouble(p.hAcc, 3))
  private val vAccField =
    CsvFieldSpec.required[DekunuPoint, Double]("vAcc", p => formatDouble(p.vAcc, 3))
  private val sAccField =
    CsvFieldSpec.required[DekunuPoint, Double]("sAcc", p => formatDouble(p.sAcc, 2))
  private val headingField =
    CsvFieldSpec.required[DekunuPoint, Double]("heading", p => formatDouble(p.heading, 5))
  private val cAccField =
    CsvFieldSpec.required[DekunuPoint, Double]("cAcc", p => formatDouble(p.cAcc, 5))
  private val numSVField =
    CsvFieldSpec.required[DekunuPoint, Int]("numSV", _.numSV.toString)
  private val gpsFixField =
    CsvFieldSpec.optional[DekunuPoint, Int]("gpsFix", p => p.gpsFix.map(_.toString).getOrElse(""))

  val fieldSpecs: NonEmptyList[CsvFieldSpec[DekunuPoint, ?]] =
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

  given flysightNormalizer: PointReader[DekunuPoint] with {
    def time(point: DekunuPoint): Instant =
      point.time.toInstant
    def altitude(point: DekunuPoint): Double =
      point.hMSL
    def speed(point: DekunuPoint): GeoVector =
      GeoVector(north = point.velN, east = point.velE, vertical = point.velD)
  }

  given csvRowDecoder: CsvRowDecoder[DekunuPoint, String] with {

    def apply(row: CsvRow[String]): DecoderResult[DekunuPoint] =
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
      yield DekunuPoint(
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

  given csvRowEncoder: CsvRowEncoder[DekunuPoint, String] with {

    def apply(row: DekunuPoint): CsvRow[String] =
      CsvRow.fromNelHeaders(flysightFields(row))

  }

  private def flysightFields(row: DekunuPoint): NonEmptyList[(String, String)] =
    fieldSpecs
      .map(f => (f.name, f.encode(row)))
      .appendList(row.extra)

  private def formatDouble(value: Double, precision: Int): String =
    String.format(s"%.${precision}f", value)

}
