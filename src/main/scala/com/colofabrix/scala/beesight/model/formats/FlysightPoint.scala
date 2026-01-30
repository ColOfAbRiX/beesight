package com.colofabrix.scala.beesight.model.formats

import cats.data.NonEmptyList
import cats.implicits.*
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.model.derivation.given
import fs2.data.csv.*
import fs2.data.csv.generic.semiauto.*
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
  gpsFix: Int,
  numSV: Int,
  extra: List[(String, String)],
)

object FlysightPoint {

  given flysightFormatAdapter: FileFormatAdapter[FlysightPoint] with {

    def toInputFlightPoint(point: FlysightPoint): InputFlightRow[FlysightPoint] =
      InputFlightRow(
        time = point.time.toInstant,
        altitude = point.hMSL,
        northSpeed = point.velN,
        eastSpeed = point.velE,
        verticalSpeed = point.velD,
        source = point,
      )

  }

  given csvRowDecoder: CsvRowDecoder[FlysightPoint, String] with {

    def apply(row: CsvRow[String]): DecoderResult[FlysightPoint] =
      for
        javaTime <- row.as[OffsetDateTime]("time")
        lat      <- row.as[Double]("lat")
        lon      <- row.as[Double]("lon")
        hMSL     <- row.as[Double]("hMSL")
        velN     <- row.as[Double]("velN")
        velE     <- row.as[Double]("velE")
        velD     <- row.as[Double]("velD")
        hAcc     <- row.as[Double]("hAcc")
        vAcc     <- row.as[Double]("vAcc")
        sAcc     <- row.as[Double]("sAcc")
        heading  <- row.as[Double]("heading")
        cAcc     <- row.as[Double]("cAcc")
        gpsFix   <- row.as[Int]("gpsFix")
        numSV    <- row.as[Int]("numSV")
      yield FlysightPoint(
        time = javaTime,
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

  given outputFlightRowEncoder: CsvRowEncoder[OutputFlightRow[FlysightPoint], String] with {

    def apply(row: OutputFlightRow[FlysightPoint]): CsvRow[String] =
      CsvRow.fromNelHeaders(
        flysightFields(row.source)
          .append(("phase", row.phase.toString)),
      )

  }

  private def flysightFields(row: FlysightPoint): NonEmptyList[(String, String)] =
    NonEmptyList
      .of(
        ("time", row.time.toString()),
        ("lat", formatDouble(row.lat, 7)),
        ("lon", formatDouble(row.lon, 7)),
        ("hMSL", formatDouble(row.hMSL, 3)),
        ("velN", formatDouble(row.velN, 2)),
        ("velE", formatDouble(row.velE, 2)),
        ("velD", formatDouble(row.velD, 2)),
        ("hAcc", formatDouble(row.hAcc, 3)),
        ("vAcc", formatDouble(row.vAcc, 3)),
        ("sAcc", formatDouble(row.sAcc, 2)),
        ("heading", formatDouble(row.heading, 5)),
        ("cAcc", formatDouble(row.cAcc, 5)),
        ("gpsFix", row.gpsFix.toString),
        ("numSV", row.numSV.toString),
      )
      .appendList(row.extra)

  private def formatDouble(value: Double, precision: Int): String =
    BigDecimal(value)
      .setScale(precision, BigDecimal.RoundingMode.HALF_UP)
      .toString()

}
