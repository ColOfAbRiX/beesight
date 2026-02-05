package com.colofabrix.scala.beesight.model.formats

import cats.data.NonEmptyList
import cats.implicits.*
import com.colofabrix.scala.beesight.detection.model.GeoVector
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.model.derivation.given
import fs2.data.csv.*
import fs2.data.csv.generic.semiauto.*
import java.time.*
import scala.util.Try

/**
 * Airlog One Data Point
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
final case class AirlogPoint(
  time: Instant,
  coordinates: Coordinates,
  altitude: Altitude,
  motion: Motion,
  orientation: Quaternion,
  acceleration: Acceleration,
  angularVelocity: AngularVelocity,
  metadata: Metadata,
  extra: List[(String, String)],
)

object AirlogPoint {

  given airlogFormatAdapter: FileFormatAdapter[AirlogPoint] with {

    def toInputFlightPoint(point: AirlogPoint): InputFlightRow[AirlogPoint] = {
      val headingRad = Math.toRadians(point.motion.heading)
      InputFlightRow(
        time = point.time,
        altitude = point.altitude.gps,
        speed = GeoVector(
          north = point.motion.speed * Math.cos(headingRad),
          east = point.motion.speed * Math.sin(headingRad),
          vertical = point.motion.sink,
        ),
        source = point,
      )
    }

  }

  given csvRowDecoder: CsvRowDecoder[AirlogPoint, String] with {

    def apply(row: CsvRow[String]): DecoderResult[AirlogPoint] =
      for
        javaTime <- row.as[Instant]("time")
        lat      <- row.as[Double]("lat")
        lon      <- row.as[Double]("lon")
        alt      <- row.as[Double]("alt")
        gpsAlt   <- row.as[Double]("gpsAlt")
        speed    <- row.as[Double]("speed")
        sink     <- row.as[Double]("sink")
        heading  <- row.as[Double]("heading")
        bSink    <- row.as[Double]("b_sink")
        qw       <- row.as[Double]("Qw")
        qx       <- row.as[Double]("Qx")
        qy       <- row.as[Double]("Qy")
        qz       <- row.as[Double]("Qz")
        ax       <- row.as[Double]("Ax")
        ay       <- row.as[Double]("Ay")
        az       <- row.as[Double]("Az")
        gx       <- row.as[Double]("Gx")
        gy       <- row.as[Double]("Gy")
        gz       <- row.as[Double]("Gz")
        mark     <- row.as[Int]("mark")
        numSat   <- row.as[Int]("numSat")
        msg      <- row.as[Int]("msg")
      yield AirlogPoint(
        time = javaTime,
        coordinates = Coordinates(lat, lon),
        altitude = Altitude(alt, gpsAlt),
        motion = Motion(speed, sink, heading, bSink),
        orientation = Quaternion(qw, qx, qy, qz),
        acceleration = Acceleration(ax, ay, az),
        angularVelocity = AngularVelocity(gx, gy, gz),
        metadata = Metadata(mark, numSat, msg),
        extra = List.empty,
      )

  }

  given csvRowEncoder: CsvRowEncoder[AirlogPoint, String] with {

    def apply(row: AirlogPoint): CsvRow[String] =
      CsvRow.fromNelHeaders(
        NonEmptyList
          .of(
            ("time", row.time.toString()),
            ("lat", formatDouble(row.coordinates.lat, 7)),
            ("lon", formatDouble(row.coordinates.lon, 7)),
            ("alt", formatDouble(row.altitude.barometric, 1)),
            ("gpsAlt", formatDouble(row.altitude.gps, 1)),
            ("speed", formatDouble(row.motion.speed, 2)),
            ("sink", formatDouble(row.motion.sink, 2)),
            ("heading", formatDouble(row.motion.heading, 1)),
            ("Qw", formatDouble(row.orientation.w, 4)),
            ("Qx", formatDouble(row.orientation.x, 4)),
            ("Qy", formatDouble(row.orientation.y, 4)),
            ("Qz", formatDouble(row.orientation.z, 4)),
            ("Ax", formatDouble(row.acceleration.x, 3)),
            ("Ay", formatDouble(row.acceleration.y, 3)),
            ("Az", formatDouble(row.acceleration.z, 3)),
            ("Gx", formatDouble(row.angularVelocity.x, 3)),
            ("Gy", formatDouble(row.angularVelocity.y, 3)),
            ("Gz", formatDouble(row.angularVelocity.z, 3)),
            ("mark", row.metadata.mark.toString),
            ("numSat", row.metadata.numSat.toString),
            ("msg", row.metadata.msg.toString),
            ("b_sink", formatDouble(row.motion.backupSink, 2)),
          )
          .appendList(
            row.extra,
          ),
      )

  }

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
final case class Coordinates(
  lat: Double,
  lon: Double,
)

/**
 * Altitude measurements
 *
 * @param barometric Barometric altitude (m)
 * @param gps GPS altitude (m)
 */
final case class Altitude(
  barometric: Double,
  gps: Double,
)

/**
 * Motion data
 *
 * @param speed Horizontal speed (likely km/h)
 * @param sink Vertical speed (likely m/s, negative during descent)
 * @param heading Direction of travel in degrees (0-360, where 0/360 is North)
 * @param backupSink Backup vertical speed calculation (likely m/s)
 */
final case class Motion(
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
final case class Quaternion(
  w: Double,
  x: Double,
  y: Double,
  z: Double,
)

/**
 * Acceleration data (likely g-force)
 *
 * @param x Acceleration on X axis
 * @param y Acceleration on Y axis
 * @param z Acceleration on Z axis
 */
final case class Acceleration(
  x: Double,
  y: Double,
  z: Double,
)

/**
 * Angular velocity data (likely deg/s)
 *
 * @param x Angular velocity on X axis
 * @param y Angular velocity on Y axis
 * @param z Angular velocity on Z axis
 */
final case class AngularVelocity(
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
final case class Metadata(
  mark: Int,
  numSat: Int,
  msg: Int,
)
