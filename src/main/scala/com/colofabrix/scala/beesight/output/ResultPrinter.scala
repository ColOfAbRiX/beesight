package com.colofabrix.scala.beesight.output

import cats.Show
import cats.implicits.given
import com.colofabrix.scala.beesight.*
import com.colofabrix.scala.beesight.config.Config
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.utils.*
import scala.io.AnsiColor.*

final class ResultPrinter private (config: Config) {

  def printStagesPipe: fs2.Pipe[IOConfig, OutputFlightPoint[?], Nothing] =
    data =>
      data
        .fold(Option.empty[FlightEvents]) { (_, point) =>
          Some(extractStages(point))
        }
        .evalMap {
          case Some(stages) => IOConfig.stdout(stages.show)
          case None         => IOConfig.stdout(s"${YELLOW}WARNING!${RESET} No data processed.")
        }
        .drainWhen(config.flags.writeCsv)

  private def extractStages(point: OutputFlightPoint[?]): FlightEvents =
    FlightEvents(
      takeoff = point.takeoff,
      freefall = point.freefall,
      canopy = point.canopy,
      landing = point.landing,
    )

  //  Show Instances  //

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

object ResultPrinter {

  def apply(config: Config): ResultPrinter =
    new ResultPrinter(config)

}
