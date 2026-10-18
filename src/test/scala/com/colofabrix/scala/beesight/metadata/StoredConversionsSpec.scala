package com.colofabrix.scala.beesight.metadata

import com.colofabrix.scala.beesight.detection.config.{ Altitude, Angle, Speed }
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.metadata.StoredConversions.*
import java.time.{ Duration, Instant }
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class StoredConversionsSpec extends AnyFreeSpec with Matchers {

  private val at     = Instant.parse("2026-01-15T10:00:00Z")
  private val point  = EventPoint(7, 1200.5, at, GeoVector(1.5, -2.5, 40.0))
  private val events = FlightEvents(
    takeoff = Some(point),
    freefall = None,
    canopy = Some(point.copy(index = 9)),
    landing = Some(point.copy(index = 10)),
  )
  private val swoop = SwoopData(
    turnInitiation = point,
    rolloutStart = point.copy(index = 11),
    maxVerticalSpeed = SpeedAtAltitude(3, Speed(60.0), Altitude(1800.0)),
    maxTotalSpeed = SpeedAtAltitude(4, Speed(75.0), Altitude(1500.0)),
    maxHorizontalSpeed = SpeedAtAltitude(5, Speed(30.0), Altitude(900.0)),
    degreesOfRotation = Angle(315.0),
    turnDirection = TurnDirection.Right,
    timeToExecuteTurn = Duration.ofMillis(3750),
    ground = Altitude(100.0),
    patternEntry = point.copy(index = 1),
    divePoint = point.copy(index = 2),
    stop = point.copy(index = 12),
    bottomOfTurn = point.copy(index = 10),
  )

  "StoredConversions" - {

    "round-trips an event point" in {
      val actual = point.toStored.toEventPoint

      actual shouldBe point
    }

    "round-trips flight events" in {
      val actual = events.toStored.toFlightEvents

      actual shouldBe events
    }

    "round-trips swoop data" in {
      val actual = swoop.toStored.toSwoopData

      actual shouldBe Right(swoop)
    }

    "accepts turn directions in any casing" in {
      val lower = swoop.toStored.copy(turnDirection = "right").toSwoopData
      val upper = swoop.toStored.copy(turnDirection = "RIGHT").toSwoopData

      lower shouldBe Right(swoop)
      upper shouldBe Right(swoop)
    }

    "rejects an unknown turn direction" in {
      val expected = Left("Unknown turn direction: sideways")

      val actual = swoop.toStored.copy(turnDirection = "Sideways").toSwoopData

      actual shouldBe expected
    }

  }

}
