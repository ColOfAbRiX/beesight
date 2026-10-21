package com.colofabrix.scala.beesight.metadata

import cats.syntax.eq.*
import com.colofabrix.scala.beesight.detection.config.{ Altitude, Angle, Speed }
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.metadata.StoredConversions.*
import io.circe.Json
import io.circe.syntax.*
import java.time.{ Duration, Instant, OffsetDateTime, ZoneOffset }
import org.scalacheck.Arbitrary
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class SidecarSpec extends AnyFreeSpec with Matchers with ScalaCheckPropertyChecks {

  private val genFiniteDouble: Arbitrary[Double] =
    Arbitrary(org.scalacheck.Gen.chooseNum(-10000.0, 10000.0))

  private given Arbitrary[Instant] =
    Arbitrary(org.scalacheck.Gen.chooseNum(0L, 4102444800000L).map(Instant.ofEpochMilli))

  private given Arbitrary[InputFingerprint] = Arbitrary {
    for {
      size  <- org.scalacheck.Gen.chooseNum(0L, Long.MaxValue)
      mtime <- Arbitrary.arbitrary[Instant].map(OffsetDateTime.ofInstant(_, ZoneOffset.UTC))
    } yield InputFingerprint(size, mtime)
  }

  private given Arbitrary[StoredPoint] = Arbitrary {
    for {
      index    <- org.scalacheck.Gen.chooseNum(0L, Long.MaxValue)
      time     <- Arbitrary.arbitrary[Instant]
      altitude <- genFiniteDouble.arbitrary
      north    <- genFiniteDouble.arbitrary
      east     <- genFiniteDouble.arbitrary
      vertical <- genFiniteDouble.arbitrary
    } yield StoredPoint(index, time, altitude, north, east, vertical)
  }

  private given Arbitrary[StoredFlightEvents] = Arbitrary {
    for {
      takeoff  <- org.scalacheck.Gen.option(Arbitrary.arbitrary[StoredPoint])
      freefall <- org.scalacheck.Gen.option(Arbitrary.arbitrary[StoredPoint])
      canopy   <- org.scalacheck.Gen.option(Arbitrary.arbitrary[StoredPoint])
      landing  <- org.scalacheck.Gen.option(Arbitrary.arbitrary[StoredPoint])
    } yield StoredFlightEvents(takeoff, freefall, canopy, landing)
  }

  private given Arbitrary[StoredSpeedAtAltitude] = Arbitrary {
    for {
      idx      <- org.scalacheck.Gen.chooseNum(0L, Long.MaxValue)
      speed    <- genFiniteDouble.arbitrary
      altitude <- genFiniteDouble.arbitrary
    } yield StoredSpeedAtAltitude(idx, speed, altitude)
  }

  private given Arbitrary[StoredSwoopData] = Arbitrary {
    for {
      turnInitiation <- Arbitrary.arbitrary[StoredPoint]
      rolloutStart   <- Arbitrary.arbitrary[StoredPoint]
      maxVertical    <- Arbitrary.arbitrary[StoredSpeedAtAltitude]
      maxTotal       <- Arbitrary.arbitrary[StoredSpeedAtAltitude]
      maxHorizontal  <- Arbitrary.arbitrary[StoredSpeedAtAltitude]
      degrees        <- genFiniteDouble.arbitrary
      direction      <- org.scalacheck.Gen.oneOf("Left", "Right")
      millis         <- org.scalacheck.Gen.chooseNum(0L, Long.MaxValue)
      ground         <- genFiniteDouble.arbitrary
      patternEntry   <- Arbitrary.arbitrary[StoredPoint]
      divePoint      <- Arbitrary.arbitrary[StoredPoint]
      stop           <- Arbitrary.arbitrary[StoredPoint]
      bottomOfTurn   <- Arbitrary.arbitrary[StoredPoint]
    } yield StoredSwoopData(
      turnInitiation,
      rolloutStart,
      maxVertical,
      maxTotal,
      maxHorizontal,
      degrees,
      direction,
      millis,
      ground,
      patternEntry,
      divePoint,
      stop,
      bottomOfTurn,
    )
  }

  private given Arbitrary[StoredCutSummary] = Arbitrary {
    for {
      applied <- Arbitrary.arbitrary[Boolean]
      total   <- org.scalacheck.Gen.chooseNum(0L, Long.MaxValue)
      kept    <- org.scalacheck.Gen.chooseNum(0L, Long.MaxValue)
      first   <- org.scalacheck.Gen.chooseNum(0L, Long.MaxValue)
      last    <- org.scalacheck.Gen.chooseNum(0L, Long.MaxValue)
    } yield StoredCutSummary(applied, total, kept, first, last)
  }

  private given Arbitrary[JumpEventsSection] = Arbitrary {
    for {
      hash   <- org.scalacheck.Gen.identifier
      events <- Arbitrary.arbitrary[StoredFlightEvents]
    } yield JumpEventsSection(ConfigHash.of(hash), events)
  }

  private given Arbitrary[SwoopSection] = Arbitrary {
    for {
      hash   <- org.scalacheck.Gen.identifier
      result <- Arbitrary.arbitrary[StoredSwoopData]
    } yield SwoopSection(ConfigHash.of(hash), result)
  }

  private given Arbitrary[CutSection] = Arbitrary {
    for {
      hash    <- org.scalacheck.Gen.identifier
      summary <- Arbitrary.arbitrary[StoredCutSummary]
    } yield CutSection(ConfigHash.of(hash), summary)
  }

  private given Arbitrary[SidecarSections] = Arbitrary {
    for {
      jumpevents <- org.scalacheck.Gen.option(Arbitrary.arbitrary[JumpEventsSection])
      swoop      <- org.scalacheck.Gen.option(Arbitrary.arbitrary[SwoopSection])
      cut        <- org.scalacheck.Gen.option(Arbitrary.arbitrary[CutSection])
    } yield SidecarSections(jumpevents, swoop, cut)
  }

  private given Arbitrary[Sidecar] = Arbitrary {
    for {
      version     <- org.scalacheck.Gen.identifier
      fingerprint <- Arbitrary.arbitrary[InputFingerprint]
      sections    <- Arbitrary.arbitrary[SidecarSections]
    } yield Sidecar(version, fingerprint, sections)
  }

  private val at          = Instant.parse("2026-01-15T10:00:00Z")
  private val fingerprint = InputFingerprint(1234L, OffsetDateTime.ofInstant(at, ZoneOffset.UTC))
  private val point       = EventPoint(7, 1200.5, at, GeoVector(1.5, -2.5, 40.0))
  private val events      = FlightEvents(
    takeoff = Some(point),
    freefall = Some(point.copy(index = 8)),
    canopy = Some(point.copy(index = 9)),
    landing = Some(point.copy(index = 10)),
  )
  private val swoop = SwoopData(
    turnInitiation = point,
    rolloutStart = point.copy(index = 11),
    maxVerticalSpeed = SpeedAtAltitude(3, Speed(60.0), Altitude(1800.0)),
    maxTotalSpeed = SpeedAtAltitude(4, Speed(75.0), Altitude(1500.0)),
    maxHorizontalSpeed = SpeedAtAltitude(5, Speed(30.0), Altitude(900.0)),
    degreesOfRotation = Angle(-270.0),
    turnDirection = TurnDirection.Left,
    timeToExecuteTurn = Duration.ofSeconds(4),
    ground = Altitude(100.0),
    patternEntry = point.copy(index = 1),
    divePoint = point.copy(index = 2),
    stop = point.copy(index = 12),
    bottomOfTurn = point.copy(index = 10),
  )
  private val cutSummary = StoredCutSummary(true, 5000, 2000, 1000, 2999)
  private val sidecar    = Sidecar
    .forInput("1.0.0", fingerprint)
    .withJumpevents(JumpEventsSection(ConfigHash.of("hash-a"), events.toStored))
    .withSwoop(SwoopSection(ConfigHash.of("hash-b"), swoop.toStored))
    .withCut(CutSection(ConfigHash.of("hash-c"), cutSummary))

  "Sidecar JSON codecs" - {

    "round-trip any sidecar losslessly" in {
      forAll { (sidecar: Sidecar) =>
        val actual = SidecarJson.decode(SidecarJson.encodeToString(sidecar))

        actual shouldBe Right(sidecar)
      }
    }

    "ignore unknown fields when decoding" in {
      val json = SidecarJson.encodeJson(sidecar).deepMerge(Json.obj("futureField" := Json.fromInt(1)))

      val actual = SidecarJson.decode(json.noSpaces)

      actual shouldBe Right(sidecar)
    }

  }

  "Sidecar" - {

    "merge replaces only the touched section" in {
      val newSwoop = SwoopSection(ConfigHash.of("hash-b2"), swoop.toStored)

      val actual = sidecar.withSwoop(newSwoop)

      actual.sections.jumpevents shouldBe sidecar.sections.jumpevents
      actual.sections.cut shouldBe sidecar.sections.cut
      actual.sections.swoop shouldBe Some(newSwoop)
    }

    "is for the matching version and input" in {
      val actual = sidecar.isFor("1.0.0", fingerprint)

      actual shouldBe true
    }

    "is not for a different version" in {
      val actual = sidecar.isFor("2.0.0", fingerprint)

      actual shouldBe false
    }

    "is not for a changed input" in {
      val actual = sidecar.isFor("1.0.0", fingerprint.copy(sizeBytes = 9999))

      actual shouldBe false
    }

    "fresh sections require a matching config hash" in {
      val stale  = sidecar.sections.swoop.filter(_.configHash === ConfigHash.of("wrong"))
      val actual = sidecar.sections.swoop.filter(_.configHash === ConfigHash.of("hash-b"))

      stale shouldBe None
      actual shouldBe sidecar.sections.swoop
    }

  }

  "ConfigHash" - {

    "matches the known SHA-256 digest" in {
      val expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

      val actual = ConfigHash.sha256("abc")

      actual.value shouldBe expected
    }

  }

}
