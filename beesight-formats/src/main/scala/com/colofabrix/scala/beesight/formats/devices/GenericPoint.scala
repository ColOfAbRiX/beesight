package com.colofabrix.scala.beesight.formats.devices

import cats.data.NonEmptyList
import com.colofabrix.scala.beesight.formats.*
import com.colofabrix.scala.beesight.formats.derivation.given
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.readers.*
import fs2.data.csv.*
import java.time.*

/**
 * Generic Data Point
 *
 * @param time Time in ISO8601 format
 * @param hMSL Height above sea level (m)
 * @param velN Velocity north (m/s)
 * @param velE Velocity east (m/s)
 * @param velD Velocity down (m/s)
 * @param numSV Number of satellites used in fix
 */
final private[beesight] case class GenericPoint(
  time: OffsetDateTime,
  hMSL: Double,
  velN: Double,
  velE: Double,
  velD: Double,
  numSV: Int,
)

private[beesight] object GenericPoint {

  val GenericSpecBuilder: CsvSpecBuilder[GenericPoint] =
    CsvSpecBuilder.forFormat[GenericPoint]

  import GenericSpecBuilder._

  private val timeField =
    required[OffsetDateTime]("time", _.time.toString)
  private val hMSLField =
    required[Double]("hMSL", p => formatDouble(p.hMSL, 3))
  private val velNField =
    required[Double]("velN", p => formatDouble(p.velN, 2))
  private val velEField =
    required[Double]("velE", p => formatDouble(p.velE, 2))
  private val velDField =
    required[Double]("velD", p => formatDouble(p.velD, 2))
  private val numSVField =
    required[Int]("numSV", _.numSV.toString)

  val fieldSpecs: NonEmptyList[CsvFieldSpec[GenericPoint, ?]] =
    NonEmptyList.of(
      timeField,
      hMSLField,
      velNField,
      velEField,
      velDField,
      numSVField,
    )

  val requiredColumns: Set[String] =
    fieldSpecs
      .toList
      .filter(_.required)
      .map(_.name)
      .toSet

  given GenericPointReader: PointReader[GenericPoint] with {
    def time(point: GenericPoint): Instant =
      point.time.toInstant
    def altitude(point: GenericPoint): Double =
      point.hMSL
    def speed(point: GenericPoint): GeoVector =
      GeoVector(north = point.velN, east = point.velE, vertical = point.velD)
  }

  given GenericGpsReader: GpsReader[GenericPoint] with {
    def horizontalAccuracy(point: GenericPoint): Option[Double] = None
    def verticalAccuracy(point: GenericPoint): Option[Double]   = None
    def speedAccuracy(point: GenericPoint): Option[Double]      = None
    def courseAccuracy(point: GenericPoint): Option[Double]     = None
    def satellites(point: GenericPoint): Option[Int]            = Some(point.numSV)
  }

  given csvRowDecoder: CsvRowDecoder[GenericPoint, String] with {

    def apply(row: CsvRow[String]): DecoderResult[GenericPoint] =
      for
        time  <- timeField.decode(row)
        hMSL  <- hMSLField.decode(row)
        velN  <- velNField.decode(row)
        velE  <- velEField.decode(row)
        velD  <- velDField.decode(row)
        numSV <- numSVField.decode(row)
      yield GenericPoint(
        time = time,
        hMSL = hMSL,
        velN = velN,
        velE = velE,
        velD = velD,
        numSV = numSV,
      )

  }

  given csvRowEncoder: CsvRowEncoder[GenericPoint, String] with {

    def apply(row: GenericPoint): CsvRow[String] =
      CsvRow.fromNelHeaders(GenericFields(row))

  }

  private def GenericFields(row: GenericPoint): NonEmptyList[(String, String)] =
    fieldSpecs
      .map(f => (f.name, f.encode(row)))

  private def formatDouble(value: Double, precision: Int): String =
    String.format(s"%.${precision}f", value)

}
