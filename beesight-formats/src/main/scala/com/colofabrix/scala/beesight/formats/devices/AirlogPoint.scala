package com.colofabrix.scala.beesight.formats.devices

import cats.data.NonEmptyList
import com.colofabrix.scala.beesight.formats.*
import com.colofabrix.scala.beesight.formats.derivation.given
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.detection.readers.*
import fs2.data.csv.*
import java.time.*

/**
 * AirlogOne Data Point
 *
 * @param time Time in ISO8601 format (UTC)
 * @param coordinates Geographic coordinates (latitude and longitude)
 * @param altitude Altitude measurements (barometric and GPS)
 * @param motion Motion data (speed, sink rate, heading)
 * @param orientation Quaternion orientation data
 * @param acceleration Acceleration data on three axes
 * @param angularVelocity Angular velocity (gyroscope) data on three axes
 * @param metadata Additional metadata information
 */
final private[beesight] case class AirlogPoint(
  time: Instant,
  coordinates: Coordinates,
  altitude: Altitude,
  motion: Motion,
  orientation: Option[Quaternion],
  acceleration: Acceleration,
  angularVelocity: AngularVelocity,
  metadata: Metadata,
  extra: List[(String, String)],
)

private[beesight] object AirlogPoint {

  val airlogSpecBuilder: CsvSpecBuilder[AirlogPoint] =
    CsvSpecBuilder.forFormat[AirlogPoint]

  import airlogSpecBuilder._

  private val timeField =
    required[Instant]("time", _.time.toString)
  private val latField =
    required[Double]("lat", p => formatDouble(p.coordinates.lat, 7))
  private val lonField =
    required[Double]("lon", p => formatDouble(p.coordinates.lon, 7))
  private val altField =
    required[Double]("alt", p => formatDouble(p.altitude.barometric, 1))
  private val gpsAltField =
    required[Double]("gpsAlt", p => formatDouble(p.altitude.gps, 1))
  private val speedField =
    required[Double]("speed", p => formatDouble(p.motion.speed, 2))
  private val sinkField =
    required[Double]("sink", p => formatDouble(p.motion.sink, 2))
  private val headingField =
    required[Double]("heading", p => formatDouble(p.motion.heading, 1))
  private val qwField =
    lenient[Double]("Qw", _.orientation.map(q => formatDouble(q.w, 4)).getOrElse(""))
  private val qxField =
    lenient[Double]("Qx", _.orientation.map(q => formatDouble(q.x, 4)).getOrElse(""))
  private val qyField =
    lenient[Double]("Qy", _.orientation.map(q => formatDouble(q.y, 4)).getOrElse(""))
  private val qzField =
    lenient[Double]("Qz", _.orientation.map(q => formatDouble(q.z, 4)).getOrElse(""))
  private val axField =
    required[Double]("Ax", p => formatDouble(p.acceleration.x, 3))
  private val ayField =
    required[Double]("Ay", p => formatDouble(p.acceleration.y, 3))
  private val azField =
    required[Double]("Az", p => formatDouble(p.acceleration.z, 3))
  private val gxField =
    required[Double]("Gx", p => formatDouble(p.angularVelocity.x, 3))
  private val gyField =
    required[Double]("Gy", p => formatDouble(p.angularVelocity.y, 3))
  private val gzField =
    required[Double]("Gz", p => formatDouble(p.angularVelocity.z, 3))
  private val markField =
    required[Int]("mark", _.metadata.mark.toString)
  private val numSatField =
    required[Int]("numSat", _.metadata.numSat.toString)
  private val msgField =
    required[Int]("msg", _.metadata.msg.toString)
  private val bSinkField =
    required[Double]("b_sink", p => formatDouble(p.motion.backupSink, 2))

  val fieldSpecs: NonEmptyList[CsvFieldSpec[AirlogPoint, ?]] =
    NonEmptyList.of(
      timeField,
      latField,
      lonField,
      altField,
      gpsAltField,
      speedField,
      sinkField,
      headingField,
      qwField,
      qxField,
      qyField,
      qzField,
      axField,
      ayField,
      azField,
      gxField,
      gyField,
      gzField,
      markField,
      numSatField,
      msgField,
      bSinkField,
    )

  val requiredColumns: Set[String] =
    fieldSpecs
      .toList
      .filter(_.required)
      .map(_.name)
      .toSet

  given airlogPointReader: PointReader[AirlogPoint] with {
    def time(point: AirlogPoint): Instant =
      point.time
    def altitude(point: AirlogPoint): Double =
      point.altitude.barometric
    def speed(point: AirlogPoint): GeoVector =
      val headingRad = Math.toRadians(point.motion.heading)
      GeoVector(
        north = point.motion.speed * Math.cos(headingRad),
        east = point.motion.speed * Math.sin(headingRad),
        vertical = point.motion.sink,
      )
  }

  given airlogGpsReader: GpsReader[AirlogPoint] with {
    def horizontalAccuracy(point: AirlogPoint): Option[Double] = None
    def verticalAccuracy(point: AirlogPoint): Option[Double]   = None
    def speedAccuracy(point: AirlogPoint): Option[Double]      = None
    def courseAccuracy(point: AirlogPoint): Option[Double]     = None
    def satellites(point: AirlogPoint): Option[Int]            = Some(point.metadata.numSat)
  }

  given csvRowDecoder: CsvRowDecoder[AirlogPoint, String] with {

    def apply(row: CsvRow[String]): DecoderResult[AirlogPoint] =
      for
        time    <- timeField.decode(row)
        lat     <- latField.decode(row)
        lon     <- lonField.decode(row)
        alt     <- altField.decode(row)
        gpsAlt  <- gpsAltField.decode(row)
        speed   <- speedField.decode(row)
        sink    <- sinkField.decode(row)
        heading <- headingField.decode(row)
        bSink   <- bSinkField.decode(row)
        qw      <- qwField.decode(row)
        qx      <- qxField.decode(row)
        qy      <- qyField.decode(row)
        qz      <- qzField.decode(row)
        ax      <- axField.decode(row)
        ay      <- ayField.decode(row)
        az      <- azField.decode(row)
        gx      <- gxField.decode(row)
        gy      <- gyField.decode(row)
        gz      <- gzField.decode(row)
        mark    <- markField.decode(row)
        numSat  <- numSatField.decode(row)
        msg     <- msgField.decode(row)
      yield AirlogPoint(
        time = time,
        coordinates = Coordinates(lat, lon),
        altitude = Altitude(alt, gpsAlt),
        motion = Motion(speed, sink, heading, bSink),
        orientation = Quaternion.fromComponents(qw, qx, qy, qz),
        acceleration = Acceleration(ax, ay, az),
        angularVelocity = AngularVelocity(gx, gy, gz),
        metadata = Metadata(mark, numSat, msg),
        extra = List.empty,
      )

  }

  given csvRowEncoder: CsvRowEncoder[AirlogPoint, String] with {

    def apply(row: AirlogPoint): CsvRow[String] =
      CsvRow.fromNelHeaders(airlogFields(row))

  }

  private def airlogFields(row: AirlogPoint): NonEmptyList[(String, String)] =
    fieldSpecs
      .map(f => (f.name, f.encode(row)))
      .appendList(row.extra)

  private def formatDouble(value: Double, precision: Int): String =
    BigDecimal(value)
      .setScale(precision, BigDecimal.RoundingMode.HALF_UP)
      .toString()

}

/**
 * Coordinates (latitude and longitude)
 *
 * @param lat Latitude (degrees)
 * @param lon Longitude (degrees)
 */
final private[beesight] case class Coordinates(
  lat: Double,
  lon: Double,
)

/**
 * Altitude measurements
 *
 * @param barometric Barometric altitude (m)
 * @param gps GPS altitude (m)
 */
final private[beesight] case class Altitude(
  barometric: Double,
  gps: Double,
)

/**
 * Motion data
 *
 * @param speed Horizontal speed
 * @param sink Vertical speed
 * @param heading Direction of travel in degrees (0-360, where 0/360 is North)
 * @param backupSink Backup vertical speed calculation
 */
final private[beesight] case class Motion(
  speed: Double,
  sink: Double,
  heading: Double,
  backupSink: Double,
)

/**
 * Quaternion orientation data
 *
 * @param w Quaternion w component
 * @param x Quaternion x component
 * @param y Quaternion y component
 * @param z Quaternion z component
 */
final private[beesight] case class Quaternion(
  w: Double,
  x: Double,
  y: Double,
  z: Double,
)

object Quaternion {

  def fromComponents(
    oW: Option[Double],
    oX: Option[Double],
    oY: Option[Double],
    oZ: Option[Double],
  ): Option[Quaternion] =
    for
      w <- oW
      x <- oX
      y <- oY
      z <- oZ
    yield Quaternion(w, x, y, z)

}

/**
 * Acceleration data
 *
 * @param x Acceleration on X axis
 * @param y Acceleration on Y axis
 * @param z Acceleration on Z axis
 */
final private[beesight] case class Acceleration(
  x: Double,
  y: Double,
  z: Double,
)

/**
 * Angular velocity data
 *
 * @param x Angular velocity on X axis
 * @param y Angular velocity on Y axis
 * @param z Angular velocity on Z axis
 */
final private[beesight] case class AngularVelocity(
  x: Double,
  y: Double,
  z: Double,
)

/**
 * Metadata information
 *
 * @param mark Event marker flag
 * @param numSat Number of GPS satellites being tracked
 * @param msg Message counter
 */
final private[beesight] case class Metadata(
  mark: Int,
  numSat: Int,
  msg: Int,
)
