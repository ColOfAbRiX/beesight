package com.colofabrix.scala.beesight

import cats.Show
import cats.implicits.*
import cats.effect.IO
import com.colofabrix.scala.beesight.PeakDetection.*
import com.colofabrix.scala.beesight.Utils.*
import fs2.*

object FlightStagesDetection:

  val WindowTime: Int            = 30 * 5
  val TakeoffThreshold: Double   = 3.5
  val LandingThreshold: Double   = 1.0
  val IgnoreLandingAbove: Double = 600.0
  val BufferPoints: Int          = 500

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
    _.through(PeakDetection(WindowTime, TakeoffThreshold, 0.9).detectStats(_.hMSL))
      .zipWithIndex
      .fold(FlightPoints()) {
        case (flightPoints @ FlightPoints(None, _, _, _, _), ((_, Peak.Stable, stats), i)) =>
          // Unkown state
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
        println("WARNING! Stable data might not contain a flight recording")
        println(flightPoints.show)
        data

      case flightPoints @ FlightPoints(Some(Point(_, None)), _, _, None, _) =>
        println("No proper takeoff or landing point detected. Collecting all data.")
        println(flightPoints.show)
        data

      case flightPoints @ FlightPoints(Some(Point(takeoff, Some(_))), _, _, None, _) =>
        println(s"Collecting data from line ${keepFrom(takeoff)} until the end")
        println(flightPoints.show)
        data.drop(keepFrom(takeoff))

      case flightPoints @ FlightPoints(Some(Point(takeoff, _)), _, _, Some(Point(landing, Some(_))), totalPoints) =>
        println(s"Collecting data from line ${keepFrom(takeoff)} to line ${keepUntil(landing, totalPoints)}")
        println(flightPoints.show)
        data.drop(keepFrom(takeoff)).take(keepUntil(landing, totalPoints))

      case flightPoints @ FlightPoints(None, _, _, Some(Point(landing, Some(_))), totalPoints) =>
        println(s"Collecting data from line 0 to line ${keepUntil(landing, totalPoints)}")
        println(flightPoints.show)
        data.take(keepUntil(landing, totalPoints))

      case flightPoints =>
        println(s"ERROR? Not sure why we're here. Collecting everything, just to be sure")
        println(flightPoints.show)
        data
    }

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
