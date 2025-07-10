package com.colofabrix.scala.beesight

import cats.Show
import cats.effect.IO
import cats.implicits.*
import com.colofabrix.scala.beesight.*
import com.colofabrix.scala.beesight.StreamUtils.*
import com.colofabrix.scala.beesight.config.*
import com.colofabrix.scala.beesight.model.*
import fs2.*
import scala.io.AnsiColor.*

final class DataCutter(config: Config) {

  def cut(points: FlightStagesPoints): Pipe[IO, FlysightPoint, FlysightPoint] =
    data =>
      points match {
        case points @ FlightStagesPoints(None, None, None, None, _) =>
          Stream.println(points.show) >>
          Stream.println(
            s"${YELLOW}WARNING!${RESET} Found stable data only. Might not contain a flight recording.",
          ) >>
          data

        case points @ FlightStagesPoints(Some(DataPoint(_, None)), _, _, None, _) =>
          Stream.println(points.show) >>
          Stream.println(s"${CYAN}No takeoff or landing points detected.${RESET} Collecting all data.") >>
          data

        case points @ FlightStagesPoints(Some(DataPoint(takeoff, Some(_))), _, _, None, _) =>
          Stream.println(points.show) >>
          retainMinPoints(points, data) {
            Stream.println(
              s"${CYAN}No landing detected.${RESET} Collecting data from line ${keepFrom(takeoff)} until the end",
            ) >>
            data.drop(keepFrom(takeoff))
          }

        case points @ FlightStagesPoints(Some(DataPoint(takeoff, _)), _, _, Some(DataPoint(landing, Some(_))), totalPoints) =>
          Stream.println(points.show) >>
          retainMinPoints(points, data) {
            Stream.println(
              s"Collecting data from line ${keepFrom(takeoff)} to line ${keepUntil(landing, totalPoints)}",
            ) >>
            data.drop(keepFrom(takeoff)).take(keepUntil(landing, totalPoints))
          }

        case points @ FlightStagesPoints(None, _, _, Some(DataPoint(landing, Some(_))), totalPoints) =>
          Stream.println(points.show) >>
          retainMinPoints(points, data) {
            Stream.println(s"${CYAN}No takeoff detected.${RESET} ") >>
            Stream.println(s"Collecting data from line 0 to line ${keepUntil(landing, totalPoints)}") >>
            data.take(keepUntil(landing, totalPoints))
          }

        case flightPoints =>
          Stream.println(flightPoints.show) >>
          Stream.println(s"${RED}ERROR?${RESET} Not sure why we're here. Collecting everything, just to be sure") >>
          data
      }

  private def retainMinPoints(
    flightPoints: FlightStagesPoints,
    data: => Stream[IO, FlysightPoint],
  )(ifMore: => Stream[IO, FlysightPoint],
  ): Stream[IO, FlysightPoint] =
    if numberOfRetainedPoints(flightPoints) < config.minRetainedPoints then
      Stream.println(s"${YELLOW}Too many points dropped!${RESET} Collecting all data.") >>
      data
    else
      ifMore
    end if

  private def numberOfRetainedPoints(flightPoints: FlightStagesPoints): Double =
    val first =
      flightPoints
        .takeoff
        .fold(0L)(_.lineIndex)
        .toDouble

    val last =
      flightPoints
        .landing
        .fold(flightPoints.lastPoint)(_.lineIndex)
        .toDouble

    (last - first) / flightPoints.lastPoint.toDouble

  private def keepFrom(index: Long): Long =
    Math.max(index - config.bufferPoints, 0)

  private def keepUntil(index: Long, max: Long): Long =
    Math.min(index + config.bufferPoints, max)

  given niceOptionShow[A: Show]: Show[Option[A]] =
    _.fold("N/A")(Show[A].show(_))

  given niceDouble: Show[Double] =
    value => f"${value}%.2f"

  given nicePoint: Show[DataPoint] =
    point => s"point ${point.lineIndex.show} at altitude ${point.altitude.show}"

  given niceFlightPoints: Show[FlightStagesPoints] =
    flightPoints =>
      s"""Flight points:
         |    Takeoff:  ${flightPoints.takeoff.show}
         |    Freefall: ${flightPoints.freefall.show}
         |    Canopy:   ${flightPoints.canopy.show}
         |    Landing:  ${flightPoints.landing.show}
         |""".stripMargin

}
