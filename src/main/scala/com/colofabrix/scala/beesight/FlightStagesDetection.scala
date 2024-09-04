package com.colofabrix.scala.beesight

import cats.Show
import cats.implicits.*
import cats.effect.IO
import com.colofabrix.scala.beesight.PeakDetection.*
import com.colofabrix.scala.beesight.Utils.*
import fs2.*

object FlightStagesDetection:

  val WindowTime: Int            = 30 * 5
  val TakeoffThreshold: Double   = 4.0
  val LandingThreshold: Double   = 0.5
  val IgnoreLandingAbove: Double = 600.0
  val BufferPoints: Int          = 500

  final case class FlightPoints(
    takeoff: Option[Long] = None,
    takeoffGround: Option[Double] = None,
    landing: Option[Long] = None,
    landingGround: Option[Double] = None,
  )

  val cutoffData: Pipe[IO, FlysightPoint, FlysightPoint] =
    data =>
      data
        .through(PeakDetection(WindowTime, TakeoffThreshold, 0.9).detectStats(_.hMSL))
        .zipWithIndex
        .fold(FlightPoints()) {
          case (FlightPoints(None, None, _, _), ((_, Peak.Stable, stats), i)) =>
            // Initial state, assuming ground
            FlightPoints(None)

          case (flightPoints @ FlightPoints(None, None, _, _), ((value, _, stats), i)) =>
            val isAboveIgnoreLine = stats.average > IgnoreLandingAbove

            if isAboveIgnoreLine then
              // We're already in flight!
              FlightPoints(
                takeoff = Some(0),
                takeoffGround = None,
              )
            else
              // Peak found, takeoff detected
              FlightPoints(
                takeoff = Some(i),
                takeoffGround = Some(stats.average),
              )

          case (flightPoints @ FlightPoints(Some(_), _, None, _), ((value, _, stats), i)) =>
            val isAboveIgnoreLine = stats.average > IgnoreLandingAbove
            val isAfterBuffering  = i > flightPoints.takeoff.get + WindowTime
            val isDataStable      = stats.stdDev < LandingThreshold

            if !isAboveIgnoreLine && isAfterBuffering && isDataStable then
              // Stable altitude, landing detected
              flightPoints.copy(
                landing = Some(i),
                landingGround = Some(stats.average),
              )
            else
              // In flight
              flightPoints

          case (flightPoints, _) =>
            // Landed
            flightPoints
        }
        .evalTap(printFlightPointstats)
        .collect {
          case FlightPoints(None, None, _, _) =>
            data
          case FlightPoints(Some(takeoff), None, _, _) =>
            data.drop(takeoff - BufferPoints)
          case FlightPoints(None, _, Some(landing), _) =>
            data.take(landing + BufferPoints)
          case FlightPoints(Some(takeoff), _, Some(landing), _) =>
            data.drop(takeoff - BufferPoints).take(landing + BufferPoints)
        }
        .flatten

  def printFlightPointstats(points: FlightPoints): IO[Unit] =
    IO.println(s"Cutoff points:") *>
    IO.println(s"    Takeoff: ${points.takeoff.show}") *>
    IO.println(s"    Takeoff ground level: ${points.takeoffGround.show}") *>
    IO.println(s"    Landing: ${points.landing.show}") *>
    IO.println(s"    Landing ground level: ${points.landingGround.show}")

  given niceDouble: Show[Double] =
    value => f"${value}%.2f"

  given niceOptionShow[A: Show]: Show[Option[A]] =
    _.fold("N/A")(Show[A].show(_))
