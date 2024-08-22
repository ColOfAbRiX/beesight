package com.colofabrix.scala.beesight

import cats.data.NonEmptyList
import cats.implicits.*
import fs2.data.csv.*
import fs2.data.csv.generic.semiauto.*
import java.time.*
import scala.util.Try

/**
 * Flysight Data Point
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
)

object FlysightPoint:

  given csvRowDecoder: CsvRowDecoder[FlysightPoint, String] with

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

    private def decodeOffsetDateTime(value: String): DecoderResult[OffsetDateTime] =
      Try(OffsetDateTime.parse(value))
        .toEither
        .leftMap(t => new DecoderError(t.getMessage()))

    given csvRowEncoder: CsvRowEncoder[FlysightPoint, String] with
      def apply(row: FlysightPoint): CsvRow[String] =
        CsvRow.fromNelHeaders(
          NonEmptyList.of(
            (row.time.toString(), "time"),
            (formatDouble(row.lat, 7), "lat"),
            (formatDouble(row.lon, 7), "lon"),
            (formatDouble(row.hMSL, 3), "hMSL"),
            (formatDouble(row.velN, 2), "velN"),
            (formatDouble(row.velE, 2), "velE"),
            (formatDouble(row.velD, 2), "velD"),
            (formatDouble(row.hAcc, 3), "hAcc"),
            (formatDouble(row.vAcc, 3), "vAcc"),
            (formatDouble(row.sAcc, 2), "sAcc"),
            (formatDouble(row.heading, 5), "heading"),
            (formatDouble(row.cAcc, 5), "cAcc"),
            (row.gpsFix.toString, "gpsFix"),
            (row.numSV.toString, "numSV"),
          ),
        )

    private def formatDouble(value: Double, precision: Int): String =
      BigDecimal(value)
        .setScale(precision, BigDecimal.RoundingMode.HALF_UP)
        .toString()
