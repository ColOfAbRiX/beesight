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

  private def genFiniteOption: org.scalacheck.Gen[Option[Double]] =
    org.scalacheck.Gen.option(genFiniteDouble.arbitrary)

  private given Arbitrary[StoredMetric] = Arbitrary {
    for {
      time                   <- Arbitrary.arbitrary[Instant]
      altitude               <- genFiniteDouble.arbitrary
      verticalSpeed          <- genFiniteDouble.arbitrary
      horizontalSpeed        <- genFiniteDouble.arbitrary
      totalSpeed             <- genFiniteDouble.arbitrary
      course                 <- genFiniteDouble.arbitrary
      courseRate             <- genFiniteDouble.arbitrary
      courseAccuracy         <- genFiniteOption
      courseAccuracyEstimate <- genFiniteOption
      glideRatio             <- genFiniteDouble.arbitrary
      liftCoefficient        <- genFiniteDouble.arbitrary
      dragCoefficient        <- genFiniteDouble.arbitrary
      diveAngle              <- genFiniteDouble.arbitrary
      diveRate               <- genFiniteDouble.arbitrary
      turnRate               <- genFiniteDouble.arbitrary
      speedAccuracy          <- genFiniteOption
      horizontalAccuracy     <- genFiniteOption
      verticalAccuracy       <- genFiniteOption
      satellites             <- org.scalacheck.Gen.option(org.scalacheck.Gen.chooseNum(0, 64))
      accelerationNorth      <- genFiniteDouble.arbitrary
      accelerationEast       <- genFiniteDouble.arbitrary
      accelerationVertical   <- genFiniteDouble.arbitrary
      accelerationMagnitude  <- genFiniteDouble.arbitrary
      accelerationAlongTrack <- genFiniteDouble.arbitrary
      accelerationCrossTrack <- genFiniteDouble.arbitrary
      totalEnergy            <- genFiniteDouble.arbitrary
      energyRate             <- genFiniteDouble.arbitrary
      sphericalErrorProbably <- genFiniteOption
      speedScoreAccuracy     <- genFiniteOption
    } yield StoredMetric(
      time,
      altitude,
      verticalSpeed,
      horizontalSpeed,
      totalSpeed,
      course,
      courseRate,
      courseAccuracy,
      courseAccuracyEstimate,
      glideRatio,
      liftCoefficient,
      dragCoefficient,
      diveAngle,
      diveRate,
      turnRate,
      speedAccuracy,
      horizontalAccuracy,
      verticalAccuracy,
      satellites,
      accelerationNorth,
      accelerationEast,
      accelerationVertical,
      accelerationMagnitude,
      accelerationAlongTrack,
      accelerationCrossTrack,
      totalEnergy,
      energyRate,
      sphericalErrorProbably,
      speedScoreAccuracy,
    )
  }

  private given Arbitrary[MetricsSection] = Arbitrary {
    for {
      hash    <- org.scalacheck.Gen.identifier
      metrics <- org.scalacheck.Gen.listOf(Arbitrary.arbitrary[StoredMetric])
    } yield MetricsSection(ConfigHash.of(hash), metrics)
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
      metrics    <- org.scalacheck.Gen.option(Arbitrary.arbitrary[MetricsSection])
      swoop      <- org.scalacheck.Gen.option(Arbitrary.arbitrary[SwoopSection])
      cut        <- org.scalacheck.Gen.option(Arbitrary.arbitrary[CutSection])
    } yield SidecarSections(jumpevents, metrics, swoop, cut)
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
  private val metric     = StoredMetric(
    at, 1200.5, 40.0, 30.0, 50.0, 315.0, 12.5, Some(1.5), Some(2.5), 0.75, 0.4, 0.1, 45.0, -3.5, 90.0,
    Some(0.5), Some(1.0), Some(1.5), Some(14), 1.5, -2.5, 9.8, 10.4, 9.5, 0.5, 2500.0, -125.0, Some(3.5), Some(0.25),
  )
  private val sidecar = Sidecar(
    "1.0.0",
    fingerprint,
    SidecarSections(
      jumpevents = Some(JumpEventsSection(ConfigHash.of("hash-a"), events.toStored)),
      metrics = Some(MetricsSection(ConfigHash.of("hash-d"), List(metric))),
      swoop = Some(SwoopSection(ConfigHash.of("hash-b"), swoop.toStored)),
      cut = Some(CutSection(ConfigHash.of("hash-c"), cutSummary)),
    ),
  )

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
