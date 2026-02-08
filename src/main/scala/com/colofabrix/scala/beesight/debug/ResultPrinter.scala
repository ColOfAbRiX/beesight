package com.colofabrix.scala.beesight.debug

import cats.Show
import cats.effect.IO
import cats.implicits.given
import com.colofabrix.scala.beesight.*
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.model.formats.FlysightPoint
import scala.io.AnsiColor.*

object ResultPrinter {

  def printStagesPipe: fs2.Pipe[IOConfig, OutputFlightPoint[FlysightPoint], Nothing] =
    data =>
      data
        .fold(Option.empty[FlightEvents]) { (_, point) =>
          Some(extractStages(point))
        }
        .evalMap {
          case Some(stages) => IOConfig.stdout(stages.show)
          case None         => IOConfig.stdout(s"${YELLOW}WARNING!${RESET} No data processed.")
        }
        .drain

  private def extractStages(point: OutputFlightPoint[FlysightPoint]): FlightEvents =
    FlightEvents(
      takeoff = point.takeoff,
      freefall = point.freefall,
      canopy = point.canopy,
      landing = point.landing,
    )

  // ─── Show Instances ────────────────────────────────────────────────────────────

  given niceOptionShow[A: Show]: Show[Option[A]] =
    _.fold("N/A")(Show[A].show(_))

  given niceDouble: Show[Double] =
    value => f"${value}%.2f"

  given nicePoint: Show[FlightEventPoint] =
    point => s"point ${point.index.show} at altitude ${point.altitude.show}"

  given niceFlightPoints: Show[FlightEvents] =
    flightPoints =>
      s"""Flight stages detected:
         |    Takeoff:  ${flightPoints.takeoff.show}
         |    Freefall: ${flightPoints.freefall.show}
         |    Canopy:   ${flightPoints.canopy.show}
         |    Landing:  ${flightPoints.landing.show}
         |""".stripMargin

}
