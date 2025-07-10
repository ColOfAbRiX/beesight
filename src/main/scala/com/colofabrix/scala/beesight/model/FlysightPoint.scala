package com.colofabrix.scala.beesight.model

import cats.data.NonEmptyList
import cats.implicits.*
import fs2.data.csv.*
import fs2.data.csv.generic.semiauto.*
import java.time.*
import scala.util.Try

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
  lat: Double = 0.0,
  lon: Double = 0.0,
  hMSL: Double = 0.0,
  velN: Double = 0.0,
  velE: Double = 0.0,
  velD: Double = 0.0,
  hAcc: Double = 0.0,
  vAcc: Double = 0.0,
  sAcc: Double = 0.0,
  heading: Double = 0.0,
  cAcc: Double = 0.0,
  gpsFix: Int = 0,
  numSV: Int = 0,
  extra: List[(String, String)] = List.empty,
)

object FlysightPoint {

  given csvRowDecoder: CsvRowDecoder[FlysightPoint, String] with {

    def apply(row: CsvRow[String]): DecoderResult[FlysightPoint] =
      for
        time     <- row.as[String]("time")
        javaTime <- decodeOffsetDateTime(time)
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
      yield FlysightPoint(javaTime, lat, lon, hMSL, velN, velE, velD, hAcc, vAcc, sAcc, heading, cAcc, gpsFix, numSV)

  }

  given csvRowEncoder: CsvRowEncoder[FlysightPoint, String] with {

    def apply(row: FlysightPoint): CsvRow[String] =
      CsvRow.fromNelHeaders(
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
          .appendList(
            row.extra,
          ),
      )

  }

  private def decodeOffsetDateTime(value: String): DecoderResult[OffsetDateTime] =
    Try(OffsetDateTime.parse(value))
      .toEither
      .leftMap(t => new DecoderError(t.getMessage()))

  private def formatDouble(value: Double, precision: Int): String =
    BigDecimal(value)
      .setScale(precision, BigDecimal.RoundingMode.HALF_UP)
      .toString()

}
