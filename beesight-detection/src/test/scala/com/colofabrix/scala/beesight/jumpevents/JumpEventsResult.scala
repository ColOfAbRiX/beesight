package com.colofabrix.scala.beesight.jumpevents

import com.colofabrix.scala.beesight.model.*
import fs2.data.csv.*
import java.time.Instant

final private[beesight] case class JumpEventsResult(
  filename: String,
  takeoff: Option[EventPoint],
  freefall: Option[EventPoint],
  canopy: Option[EventPoint],
  landing: Option[EventPoint],
  tag: Option[String],
) {

  def toFlightEvents: FlightEvents =
    FlightEvents(takeoff, freefall, canopy, landing)

}

private[beesight] object JumpEventsResult {

  private def optLong(row: CsvRow[String], name: String): DecoderResult[Option[Long]] =
    row.as[String](name).map { s =>
      val trimmed = s.trim
      if trimmed.isEmpty then None else Some(trimmed.toLong)
    }

  private def optString(row: CsvRow[String], name: String): DecoderResult[Option[String]] =
    row.as[String](name).map { s =>
      val trimmed = s.trim
      if trimmed.isEmpty then None else Some(trimmed)
    }

  given CsvRowDecoder[JumpEventsResult, String] with
    def apply(row: CsvRow[String]): DecoderResult[JumpEventsResult] =
      for
        filename <- row.as[String]("filename")
        takeoff  <- optLong(row, "takeoff_pt")
        freefall <- optLong(row, "freefall_pt")
        canopy   <- optLong(row, "canopy_pt")
        landing  <- optLong(row, "landing_pt")
        tag      <- optString(row, "tag")
      yield JumpEventsResult(
        filename = filename,
        takeoff = takeoff.map(EventPoint(_, 0.0, Instant.EPOCH, GeoVector.zero)),
        freefall = freefall.map(EventPoint(_, 0.0, Instant.EPOCH, GeoVector.zero)),
        canopy = canopy.map(EventPoint(_, 0.0, Instant.EPOCH, GeoVector.zero)),
        landing = landing.map(EventPoint(_, 0.0, Instant.EPOCH, GeoVector.zero)),
        tag = tag,
      )

}
