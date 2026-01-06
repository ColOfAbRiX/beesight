package com.colofabrix.scala.beesight

import cats.Show
import cats.effect.IO
import cats.implicits.*
import com.colofabrix.scala.beesight.config.*
import com.colofabrix.scala.beesight.model.*
import fs2.*
import scala.io.AnsiColor.*

final class DataCutter(config: Config) {

  def cut(points: FlightStagesPoints): Pipe[IO, FlysightPoint, FlysightPoint] =
    data =>
      points match {
        case points @ FlightStagesPoints(None, None, None, None, _, _) =>
          val msg = s"${YELLOW}WARNING!${RESET} Found stable data only. Might not contain a flight recording."
          fs2Println(points.show) >>
          fs2Println(msg) >>
          data

        case points @ FlightStagesPoints(Some(FlightStagePoint(takeoff, _)), _, _, None, _, _) =>
          val msg = s"${CYAN}No landing detected.${RESET} Collecting data from line ${keepFrom(takeoff)} until the end"
          fs2Println(points.show) >>
          retainMinPoints(points, data) {
            fs2Println(msg) >>
            data.drop(keepFrom(takeoff))
          }

        case points @ FlightStagesPoints(Some(FlightStagePoint(to, _)), _, _, Some(FlightStagePoint(landing, _)), tp, _) =>
          fs2Println(points.show) >>
          retainMinPoints(points, data) {
            val msg = s"Collecting data from line ${keepFrom(to)} to line ${keepUntil(landing, tp)}"
            fs2Println(msg) >>
            data.drop(keepFrom(to)).take(keepUntil(landing, tp))
          }

        case points @ FlightStagesPoints(None, _, _, Some(FlightStagePoint(landing, _)), totalPoints, _) =>
          fs2Println(points.show) >>
          retainMinPoints(points, data) {
            fs2Println(s"${CYAN}No takeoff detected.${RESET} ") >>
            fs2Println(s"Collecting data from line 0 to line ${keepUntil(landing, totalPoints)}") >>
            data.take(keepUntil(landing, totalPoints))
          }

        case flightPoints =>
          fs2Println(flightPoints.show) >>
          fs2Println(s"${RED}ERROR?${RESET} Not sure why we're here. Collecting everything, just to be sure") >>
          data
      }

  private def retainMinPoints(
    flightPoints: FlightStagesPoints,
    data: => Stream[IO, FlysightPoint],
  )(ifMore: => Stream[IO, FlysightPoint],
  ): Stream[IO, FlysightPoint] =
    if numberOfRetainedPoints(flightPoints) < config.minRetainedPoints then
      fs2Println(s"${YELLOW}Too many points dropped!${RESET} Collecting all data.") >>
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

  private def fs2Println[A: cats.Show](a: => A): Stream[IO, Unit] =
    Stream.eval(IO.println(a))

  given niceOptionShow[A: Show]: Show[Option[A]] =
    _.fold("N/A")(Show[A].show(_))

  given niceDouble: Show[Double] =
    value => f"${value}%.2f"

  given nicePoint: Show[FlightStagePoint] =
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
