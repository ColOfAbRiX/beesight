package com.colofabrix.scala.beesight

import cats.effect.IO
import cats.implicits.*
import cats.Show
import com.colofabrix.scala.beesight.StreamUtils.*
import com.colofabrix.scala.stats.PeakDetection
import com.colofabrix.scala.stats.PeakDetection.*
import fs2.*
import scala.io.AnsiColor._

final class FlightStagesDetection(config: Config):
  import config.detectionConfig.*

  final case class FlightPoints(
    takeoff: Option[Point] = None,
    freefall: Option[Point] = None,
    canopy: Option[Point] = None,
    landing: Option[Point] = None,
    lastPoint: Long = 0,
  )

  final case class Point(
    lineIndex: Long,
    altitude: Option[Double] = None,
  )

  val flightPoints: Pipe[IO, FlysightPoint, FlightPoints] =
    _.through(PeakDetection(WindowTime, TakeoffThreshold, Influence).detectStats(_.hMSL))
      .zipWithIndex
      .fold(FlightPoints()) {
        case (flightPoints @ FlightPoints(None, _, _, _, _), ((_, Peak.Stable, stats), i)) =>
          // Unknown state
          val isAboveIgnoreLine = stats.average > IgnoreLandingAbove
          val isAfterBuffering  = i > WindowTime
          val isDataStable      = stats.stdDev < LandingThreshold

          if !isAboveIgnoreLine && isAfterBuffering && isDataStable then
            // Stable altitude, landing detected
            flightPoints.copy(landing = Some(Point(i, Some(stats.average))), lastPoint = i)
          else
            // In flight
            flightPoints.copy(lastPoint = i)

        case (FlightPoints(None, _, _, _, _), ((_, _, stats), i)) =>
          // We detected a peak. Is it because we took of or because we're climbing?
          val isAboveIgnoreLine = stats.average > IgnoreLandingAbove

          if isAboveIgnoreLine then
            // We're already in flight and missed the beginning!
            FlightPoints(takeoff = Some(Point(0, None)), lastPoint = i)
          else
            // Peak found, takeoff detected
            FlightPoints(takeoff = Some(Point(i, Some(stats.average))), lastPoint = i)

        case (flightPoints @ FlightPoints(Some(Point(takeoff, _)), _, _, None, _), ((_, _, stats), i)) =>
          // We're in flight and we need to look for interesting points
          val isAboveIgnoreLine = stats.average > IgnoreLandingAbove
          val isAfterBuffering  = i > takeoff + WindowTime
          val isDataStable      = stats.stdDev < LandingThreshold

          if !isAboveIgnoreLine && isAfterBuffering && isDataStable then
            // Stable altitude, landing detected
            flightPoints.copy(landing = Some(Point(i, Some(stats.average))), lastPoint = i)
          else
            // In flight
            flightPoints.copy(lastPoint = i)

        case (flightPoints, ((_, _, _), i)) =>
          // Landed
          flightPoints.copy(lastPoint = i)
      }

  def cutData(data: Stream[IO, FlysightPoint]): Pipe[IO, FlightPoints, FlysightPoint] =
    _.flatMap {
      case flightPoints @ FlightPoints(None, None, None, None, _) =>
        Stream.ioPrintln(flightPoints.show) >>
        Stream.ioPrintln(s"${YELLOW}WARNING!${RESET} Stable data might not contain a flight recording.") >>
        data

      case flightPoints @ FlightPoints(Some(Point(_, None)), _, _, None, _) =>
        Stream.ioPrintln(flightPoints.show) >>
        Stream.ioPrintln(s"${CYAN}No takeoff or landing points detected.${RESET} Collecting all data.") >>
        data

      case flightPoints @ FlightPoints(Some(Point(takeoff, Some(_))), _, _, None, _) =>
        Stream.ioPrintln(flightPoints.show) >>
        retainMinPoints(flightPoints, data) {
          Stream.ioPrintln(s"${CYAN}No landing detected.${RESET} Collecting data from line ${keepFrom(takeoff)} until the end") >>
          data.drop(keepFrom(takeoff))
        }

      case flightPoints @ FlightPoints(Some(Point(takeoff, _)), _, _, Some(Point(landing, Some(_))), totalPoints) =>
        Stream.ioPrintln(flightPoints.show) >>
        retainMinPoints(flightPoints, data) {
          Stream.ioPrintln(s"Collecting data from line ${keepFrom(takeoff)} to line ${keepUntil(landing, totalPoints)}") >>
          data.drop(keepFrom(takeoff)).take(keepUntil(landing, totalPoints))
        }

      case flightPoints @ FlightPoints(None, _, _, Some(Point(landing, Some(_))), totalPoints) =>
        Stream.ioPrintln(flightPoints.show) >>
        retainMinPoints(flightPoints, data) {
          Stream.ioPrintln(s"${CYAN}No takeoff detected.${RESET} ") >>
          Stream.ioPrintln(s"Collecting data from line 0 to line ${keepUntil(landing, totalPoints)}") >>
          data.take(keepUntil(landing, totalPoints))
        }

      case flightPoints =>
        Stream.ioPrintln(flightPoints.show) >>
        Stream.ioPrintln(s"${RED}ERROR?${RESET} Not sure why we're here. Collecting everything, just to be sure") >>
        data
    }

  def retainMinPoints(
    flightPoints: FlightPoints,
    data: => Stream[IO, FlysightPoint],
  )(ifMore: => Stream[IO, FlysightPoint],
  ): Stream[IO, FlysightPoint] =
    if retainedPoints(flightPoints) < MinRetainedPoints then
      Stream.ioPrintln(s"${YELLOW}Too many points dropped!${RESET} Collecting all data.") >>
      data
    else
      ifMore
    end if

  def retainedPoints(flightPoints: FlightPoints): Double =
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

  def keepFrom(index: Long): Long =
    Math.max(index - BufferPoints, 0)

  def keepUntil(index: Long, max: Long): Long =
    Math.min(index + BufferPoints, max)

  //  ----  //

  given niceOptionShow[A: Show]: Show[Option[A]] =
    _.fold("N/A")(Show[A].show(_))

  given niceDouble: Show[Double] =
    value => f"${value}%.2f"

  given nicePoint: Show[Point] =
    point => s"point ${point.lineIndex.show} at altitude ${point.altitude.show}"

  given niceFlightPoints: Show[FlightPoints] =
    flightPoints =>
      s"""Flight points:
         |    Takeoff:  ${flightPoints.takeoff.show}
         |    Freefall: ${flightPoints.freefall.show}
         |    Canopy:   ${flightPoints.canopy.show}
         |    Landing:  ${flightPoints.landing.show}
         |""".stripMargin
