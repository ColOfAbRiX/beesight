package com.colofabrix.scala.beesight.output

import com.colofabrix.scala.beesight.detection.config.{ Altitude, Angle, Speed }
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.metadata.StoredConversions.*
import com.colofabrix.scala.beesight.metadata.StoredSwoopData
import io.circe.parser
import java.time.{ Duration, Instant }
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class SwoopReportWriterSpec extends AnyFreeSpec with Matchers {

  "SwoopReportWriter" - {

    "renders the swoop analysis as JSON" in {
      val expected = Right(swoop.toStored)

      val actual = parser.decode[StoredSwoopData](SwoopReportWriter.toJson(swoop))

      actual shouldBe expected
    }

    "round-trips through the metadata file codecs" in {
      val expected = Right(swoop)

      val actual = parser.decode[StoredSwoopData](SwoopReportWriter.toJson(swoop)).flatMap(_.toSwoopData)

      actual shouldBe expected
    }

    "renders the JSON indented over multiple lines" in {
      val actual = SwoopReportWriter.toJson(swoop)

      actual.linesIterator.toList.head shouldBe "{"
      actual.linesIterator.toList(1) should startWith("  \"")
      actual.linesIterator.toList.last shouldBe "}"
    }

  }

  private lazy val at    = Instant.parse("2026-01-15T10:00:00Z")
  private lazy val point = EventPoint(7, 1200.5, at, GeoVector(1.5, -2.5, 40.0))
  private lazy val swoop =
    SwoopData(
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

}
