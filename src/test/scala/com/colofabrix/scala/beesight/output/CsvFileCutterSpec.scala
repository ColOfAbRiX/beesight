package com.colofabrix.scala.beesight.output

import com.colofabrix.scala.beesight.config.{ Config, DataCutterConfig }
import com.colofabrix.scala.beesight.detection.config.{ KeepPoints, Percent }
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.metadata.StoredConversions.*
import com.colofabrix.scala.beesight.metadata.StoredCutSummary
import io.github.iltotore.iron.*
import java.time.Instant
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class CsvFileCutterSpec extends AnyFreeSpec with Matchers {

  private val time = Instant.parse("2026-01-15T10:00:00Z")

  private val rows: Vector[(FlightEvents, Int)] = (0L to 9L).toVector.map { index =>
    val takeoff = Option.when(index >= 4L)(EventPoint(4L, 3000.0, time, GeoVector.zero))
    val landing = Option.when(index >= 7L)(EventPoint(6L, 100.0, time, GeoVector.zero))
    (FlightEvents(takeoff, None, None, landing), index.toInt)
  }

  private def cutter(config: DataCutterConfig): CsvFileCutter =
    CsvFileCutter(Config.default.copy(dataCutterConfig = config))

  "CsvFileCutter" - {

    "reports the retained window when the cut is applied" in {
      val config   = DataCutterConfig(KeepPoints(0), KeepPoints(0), Percent(0.1))
      val expected = CsvFileCutter.CutSummary(true, 10, 3, 4, 6)

      val actual = cutter(config).cutVector(rows)

      actual.summary shouldBe expected
      actual.retained shouldBe Vector(rows(4), rows(5), rows(6))
    }

    "keeps the configured points before takeoff" in {
      val config   = DataCutterConfig(KeepPoints(2), KeepPoints(0), Percent(0.1))
      val expected = CsvFileCutter.CutSummary(true, 10, 5, 2, 6)

      val actual = cutter(config).cutVector(rows)

      actual.summary shouldBe expected
      actual.retained shouldBe Vector(rows(2), rows(3), rows(4), rows(5), rows(6))
    }

    "keeps the file uncut when too few points would be retained" in {
      val config   = DataCutterConfig(KeepPoints(0), KeepPoints(0), Percent(0.5))
      val expected = CsvFileCutter.CutSummary(false, 10, 10, 0, 9)

      val actual = cutter(config).cutVector(rows)

      actual.summary shouldBe expected
      actual.retained shouldBe rows
    }

    "converts the summary to the metadata file format" in {
      val config   = DataCutterConfig(KeepPoints(0), KeepPoints(0), Percent(0.1))
      val expected = StoredCutSummary(true, 10, 3, 4, 6)

      val actual = cutter(config).cutVector(rows).summary.toStored

      actual shouldBe expected
    }

  }

}
