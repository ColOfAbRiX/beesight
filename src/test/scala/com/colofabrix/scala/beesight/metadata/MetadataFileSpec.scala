package com.colofabrix.scala.beesight.metadata

import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.metadata.StoredConversions.*
import io.circe.Json
import io.circe.syntax.*
import java.time.Instant
import org.scalacheck.Arbitrary
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class MetadataFileSpec extends AnyFreeSpec with Matchers with ScalaCheckPropertyChecks {

  private given Arbitrary[Instant] =
    Arbitrary(org.scalacheck.Gen.chooseNum(0L, 4102444800000L).map(Instant.ofEpochMilli))

  private given Arbitrary[StoredPoint] = Arbitrary {
    for {
      index    <- org.scalacheck.Gen.chooseNum(0L, Long.MaxValue)
      time     <- Arbitrary.arbitrary[Instant]
      altitude <- org.scalacheck.Gen.chooseNum(-10000.0, 10000.0)
      north    <- org.scalacheck.Gen.chooseNum(-10000.0, 10000.0)
      east     <- org.scalacheck.Gen.chooseNum(-10000.0, 10000.0)
      vertical <- org.scalacheck.Gen.chooseNum(-10000.0, 10000.0)
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

  private given Arbitrary[StoredCutSummary] = Arbitrary {
    for {
      applied <- Arbitrary.arbitrary[Boolean]
      total   <- org.scalacheck.Gen.chooseNum(0L, Long.MaxValue)
      kept    <- org.scalacheck.Gen.chooseNum(0L, Long.MaxValue)
      first   <- org.scalacheck.Gen.chooseNum(0L, Long.MaxValue)
      last    <- org.scalacheck.Gen.chooseNum(0L, Long.MaxValue)
    } yield StoredCutSummary(applied, total, kept, first, last)
  }

  private given Arbitrary[MetadataFile] = Arbitrary {
    for {
      version    <- org.scalacheck.Gen.identifier
      configHash <- org.scalacheck.Gen.identifier
      jumpevents <- org.scalacheck.Gen.option(Arbitrary.arbitrary[StoredFlightEvents])
      cut        <- org.scalacheck.Gen.option(Arbitrary.arbitrary[StoredCutSummary])
    } yield MetadataFile(version, ConfigHash.of(configHash), jumpevents, cut)
  }

  private val at           = Instant.parse("2026-01-15T10:00:00Z")
  private val point        = EventPoint(7, 1200.5, at, GeoVector(1.5, -2.5, 40.0))
  private val events       = FlightEvents(
    takeoff = Some(point),
    freefall = Some(point.copy(index = 8)),
    canopy = Some(point.copy(index = 9)),
    landing = Some(point.copy(index = 10)),
  )
  private val cutSummary   = StoredCutSummary(true, 5000, 2000, 1000, 2999)
  private val metadataFile = MetadataFile(
    "1.0.0",
    configHash = ConfigHash.of("hash-a"),
    jumpevents = Some(events.toStored),
    cut = Some(cutSummary),
  )

  "MetadataFile JSON codecs" - {

    "round-trip any metadata file losslessly" in {
      forAll { (file: MetadataFile) =>
        val actual = MetadataFileJson.decode(MetadataFileJson.encodeToString(file))

        actual shouldBe Right(file)
      }
    }

    "ignore unknown fields when decoding" in {
      val json = MetadataFileJson.encodeJson(metadataFile).deepMerge(Json.obj("futureField" := Json.fromInt(1)))

      val actual = MetadataFileJson.decode(json.noSpaces)

      actual shouldBe Right(metadataFile)
    }

    "decode files with obsolete sections as empty" in {
      val obsolete = Json.obj(
        "beesightVersion" := "1.0.0",
        "sections"        := Json.obj("jumpevents" := Json.obj(), "metrics" := Json.obj()),
      )

      val actual = MetadataFileJson.decode(obsolete.noSpaces)

      actual.isLeft shouldBe true
    }

  }

  "MetadataFile" - {

    "fresh detection requires a matching config hash" in {
      val stale = metadataFile.configHash === ConfigHash.of("wrong")
      val fresh = metadataFile.configHash === ConfigHash.of("hash-a")

      stale shouldBe false
      fresh shouldBe true
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
