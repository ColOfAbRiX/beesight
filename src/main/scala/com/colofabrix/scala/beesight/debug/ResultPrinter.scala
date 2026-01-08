package com.colofabrix.scala.beesight.debug

import cats.effect.IO
import cats.implicits.given
import cats.Show
import com.colofabrix.scala.beesight.*
import com.colofabrix.scala.beesight.model.*
import scala.io.AnsiColor.*

object ResultPrinter {

  def printStagesPipe: fs2.Pipe[IOConfig, OutputFlightPoint[FlysightPoint], Nothing] =
    data =>
      data
        .fold(Option.empty[FlightStagesPoints]) { (_, point) =>
          Some(extractStages(point))
        }
        .evalMap {
          case Some(stages) => IOConfig.stdout(stages.show)
          case None         => IOConfig.stdout(s"${YELLOW}WARNING!${RESET} No data processed.")
        }
        .drain

  private def extractStages(point: OutputFlightPoint[FlysightPoint]): FlightStagesPoints =
    FlightStagesPoints(
      takeoff = point.takeoff,
      freefall = point.freefall,
      canopy = point.canopy,
      landing = point.landing,
      lastPoint = point.lastPoint,
      isValid = point.isValid,
    )

  // ─── Show Instances ────────────────────────────────────────────────────────────

  given niceOptionShow[A: Show]: Show[Option[A]] =
    _.fold("N/A")(Show[A].show(_))

  given niceDouble: Show[Double] =
    value => f"${value}%.2f"

  given nicePoint: Show[FlightStagePoint] =
    point => s"point ${point.lineIndex.show} at altitude ${point.altitude.show}"

  given niceFlightPoints: Show[FlightStagesPoints] =
    flightPoints =>
      s"""Flight stages detected:
         |    Takeoff:  ${flightPoints.takeoff.show}
         |    Freefall: ${flightPoints.freefall.show}
         |    Canopy:   ${flightPoints.canopy.show}
         |    Landing:  ${flightPoints.landing.show}
         |    Valid:    ${if flightPoints.isValid then s"${GREEN}Yes${RESET}" else s"${RED}No${RESET}"}""".stripMargin

}
