package com.colofabrix.scala.beesight.output

import cats.Show
import cats.implicits.given
import com.colofabrix.scala.beesight.*
import com.colofabrix.scala.beesight.config.Config
import com.colofabrix.scala.beesight.model.*
import scala.io.AnsiColor.*

/**
 * Prints detected flight stage summary to standard output.
 */
final private[beesight] class ResultPrinter private (config: Config) {

  /**
   * Streaming pipe that collects the last detection state and prints a human-readable summary.
   */
  def printStagesPipe: fs2.Pipe[IOConfig, (FlightEvents, ?), Nothing] =
    data =>
      if config.flags.writeCsv then
        data
          .fold(Option.empty[FlightEvents]) {
            case (_, (events, _)) =>
              Some(extractStages(events))
          }
          .evalMap {
            case Some(stages) => IOConfig.stdout(stages.show)
            case None         => IOConfig.stdout(s"${YELLOW}WARNING!${RESET} No data processed.")
          }
          .drain
      else
        data.drain

  private def extractStages(events: FlightEvents): FlightEvents =
    FlightEvents(
      takeoff = events.takeoff,
      freefall = events.freefall,
      canopy = events.canopy,
      landing = events.landing,
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

private[beesight] object ResultPrinter {

  def apply(config: Config): ResultPrinter =
    new ResultPrinter(config)

}
