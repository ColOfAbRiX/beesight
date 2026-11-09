package com.colofabrix.scala.beesight.detection.swoop

import cats.effect.IO
import com.colofabrix.scala.beesight.IOValues
import com.colofabrix.scala.beesight.detection.config.{ Altitude, Angle, BeesightConfig, Speed }
import com.colofabrix.scala.beesight.detection.model.{ EventPoint, FlightEvents, GeoVector, SpeedAtAltitude, SwoopData, TurnDirection }
import com.colofabrix.scala.beesight.detection.readers.PointReader
import java.time.{ Duration, Instant }
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class SwoopAnalyserSpec extends AnyFreeSpec with Matchers with IOValues {

  private val config = BeesightConfig.default.swoop

  //  Helpers  //

  // (altitude AGL, horizontal speed, descent rate) per point, one second apart, heading due north
  private val profile = Vector(
    (1170.0, 20.0, 5.0),
    (770.0, 20.0, 5.0),
    (670.0, 22.0, 6.0),
    (530.0, 21.0, 10.0),
    (390.0, 20.0, 20.0),
    (270.0, 19.0, 30.0),
    (190.0, 18.0, 20.0),
    (120.0, 10.0, 10.0),
    (70.0, 8.0, 4.0),
    (40.0, 5.0, 2.0),
    (20.0, 2.0, 1.0),
    (10.0, 0.5, 0.0),
    (5.0, 0.5, 0.0),
    (0.0, 0.5, 0.0),
  )

  private def series(altOffset: Double): fs2.Stream[IO, SwoopPoint] =
    fs2.Stream.emits(profile.zipWithIndex.map {
      case ((altitude, hSpeed, vSpeed), index) =>
        SwoopPoint(
          time = Instant.ofEpochMilli(index * 1000L),
          altitude = altitude + altOffset,
          speed = GeoVector(north = hSpeed, east = 0.0, vertical = vSpeed),
        )
    })

  private val events =
    FlightEvents(
      takeoff = None,
      freefall = None,
      canopy = Some(EventPoint(index = 2L, altitude = 670.0, Instant.ofEpochMilli(2000L), GeoVector.zero)),
      landing = None,
    )

  //  Tests  //

  "SwoopAnalyser" - {

    "reports the swoop relative to the known ground instead of the detected one" in {
      val input = series(altOffset = 1000.0)

      val expected = SwoopData(
        turnInitiation = EventPoint(index = 2L, altitude = 680.0, Instant.ofEpochMilli(2000L), GeoVector(22.0, 0.0, 6.0)),
        rolloutStart = EventPoint(index = 5L, altitude = 280.0, Instant.ofEpochMilli(5000L), GeoVector(19.0, 0.0, 30.0)),
        maxVerticalSpeed = SpeedAtAltitude(idx = 5L, speed = Speed(30.0), altitude = Altitude(280.0)),
        maxTotalSpeed = SpeedAtAltitude(idx = 5L, speed = Speed(Math.sqrt(1261.0)), altitude = Altitude(280.0)),
        maxHorizontalSpeed = SpeedAtAltitude(idx = 5L, speed = Speed(19.0), altitude = Altitude(280.0)),
        degreesOfRotation = Angle(0.0),
        turnDirection = TurnDirection.Right,
        timeToExecuteTurn = Duration.ofSeconds(3),
        ground = Altitude(990.0),
        patternEntry = EventPoint(index = 2L, altitude = 680.0, Instant.ofEpochMilli(2000L), GeoVector(22.0, 0.0, 6.0)),
        divePoint = EventPoint(index = 5L, altitude = 280.0, Instant.ofEpochMilli(5000L), GeoVector(19.0, 0.0, 30.0)),
        stop = EventPoint(index = 11L, altitude = 20.0, Instant.ofEpochMilli(11000L), GeoVector(0.5, 0.0, 0.0)),
        bottomOfTurn = EventPoint(index = 9L, altitude = 50.0, Instant.ofEpochMilli(9000L), GeoVector(5.0, 0.0, 2.0)),
      )

      val actual = SwoopAnalyser(config, Some(Altitude(990.0))).analyse(events, input).result()

      actual shouldBe Some(expected)
    }

    "detects the ground when no known ground is provided" in {
      val input = series(altOffset = 0.0)

      val expected = SwoopData(
        turnInitiation = EventPoint(index = 2L, altitude = 670.0, Instant.ofEpochMilli(2000L), GeoVector(22.0, 0.0, 6.0)),
        rolloutStart = EventPoint(index = 5L, altitude = 270.0, Instant.ofEpochMilli(5000L), GeoVector(19.0, 0.0, 30.0)),
        maxVerticalSpeed = SpeedAtAltitude(idx = 5L, speed = Speed(30.0), altitude = Altitude(270.0)),
        maxTotalSpeed = SpeedAtAltitude(idx = 5L, speed = Speed(Math.sqrt(1261.0)), altitude = Altitude(270.0)),
        maxHorizontalSpeed = SpeedAtAltitude(idx = 5L, speed = Speed(19.0), altitude = Altitude(270.0)),
        degreesOfRotation = Angle(0.0),
        turnDirection = TurnDirection.Right,
        timeToExecuteTurn = Duration.ofSeconds(3),
        ground = Altitude(0.0),
        patternEntry = EventPoint(index = 2L, altitude = 670.0, Instant.ofEpochMilli(2000L), GeoVector(22.0, 0.0, 6.0)),
        divePoint = EventPoint(index = 5L, altitude = 270.0, Instant.ofEpochMilli(5000L), GeoVector(19.0, 0.0, 30.0)),
        stop = EventPoint(index = 11L, altitude = 10.0, Instant.ofEpochMilli(11000L), GeoVector(0.5, 0.0, 0.0)),
        bottomOfTurn = EventPoint(index = 9L, altitude = 40.0, Instant.ofEpochMilli(9000L), GeoVector(5.0, 0.0, 2.0)),
      )

      val actual = SwoopAnalyser(config).analyse(events, input).result()

      actual shouldBe Some(expected)
    }

  }

}

final private case class SwoopPoint(
  time: Instant,
  altitude: Double,
  speed: GeoVector,
)

private object SwoopPoint {

  given PointReader[SwoopPoint] with {
    def time(point: SwoopPoint): Instant    = point.time
    def altitude(point: SwoopPoint): Double = point.altitude
    def speed(point: SwoopPoint): GeoVector = point.speed
  }

}
