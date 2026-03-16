package com.colofabrix.scala.beesight.model.formats

import cats.data.NonEmptyList
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.model.derivation.given
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
 * @param heading
 * @param cAcc
 * @param gpsFix GPS fix type (3 = 3D)
 * @param numSV Number of satellites used in fix
 */
final case class FlysightPoint(
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

object FlysightPoint {

  private val timeField    = CsvFieldSpec.required[FlysightPoint, OffsetDateTime]("time", _.time.toString)
  private val latField     = CsvFieldSpec.required[FlysightPoint, Double]("lat", p => formatDouble(p.lat, 7))
  private val lonField     = CsvFieldSpec.required[FlysightPoint, Double]("lon", p => formatDouble(p.lon, 7))
  private val hMSLField    = CsvFieldSpec.required[FlysightPoint, Double]("hMSL", p => formatDouble(p.hMSL, 3))
  private val velNField    = CsvFieldSpec.required[FlysightPoint, Double]("velN", p => formatDouble(p.velN, 2))
  private val velEField    = CsvFieldSpec.required[FlysightPoint, Double]("velE", p => formatDouble(p.velE, 2))
  private val velDField    = CsvFieldSpec.required[FlysightPoint, Double]("velD", p => formatDouble(p.velD, 2))
  private val hAccField    = CsvFieldSpec.required[FlysightPoint, Double]("hAcc", p => formatDouble(p.hAcc, 3))
  private val vAccField    = CsvFieldSpec.required[FlysightPoint, Double]("vAcc", p => formatDouble(p.vAcc, 3))
  private val sAccField    = CsvFieldSpec.required[FlysightPoint, Double]("sAcc", p => formatDouble(p.sAcc, 2))
  private val headingField = CsvFieldSpec.required[FlysightPoint, Double]("heading", p => formatDouble(p.heading, 5))
  private val cAccField    = CsvFieldSpec.required[FlysightPoint, Double]("cAcc", p => formatDouble(p.cAcc, 5))
  private val numSVField   = CsvFieldSpec.required[FlysightPoint, Int]("numSV", _.numSV.toString)
  private val gpsFixField  =
    CsvFieldSpec.optional[FlysightPoint, Int]("gpsFix", p => p.gpsFix.map(_.toString).getOrElse(""))

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

  given flysightFormatAdapter: FileFormatAdapter[FlysightPoint] with {

    def toDataPoint(point: FlysightPoint): DataPoint =
      DataPoint(
        time = point.time.toInstant,
        altitude = point.hMSL,
        speed = GeoVector(north = point.velN, east = point.velE, vertical = point.velD),
      )

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

  given outputFlightRowEncoder: CsvRowEncoder[OutputFlightPoint[FlysightPoint], String] with {

    def apply(row: OutputFlightPoint[FlysightPoint]): CsvRow[String] =
      CsvRow.fromNelHeaders(
        flysightFields(row.source).append(("phase", row.phase.toString)),
      )

  }

  private def flysightFields(row: FlysightPoint): NonEmptyList[(String, String)] =
    fieldSpecs
      .map(f => (f.name, f.encode(row)))
      .appendList(row.extra)

  private def formatDouble(value: Double, precision: Int): String =
    String.format(s"%.${precision}f", value)

}
