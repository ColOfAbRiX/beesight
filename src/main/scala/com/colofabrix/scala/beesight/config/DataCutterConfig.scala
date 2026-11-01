package com.colofabrix.scala.beesight.config

import cats.syntax.all.*
import com.colofabrix.scala.beesight.detection.config.*
import com.monovore.decline.Opts
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.*
import io.github.iltotore.iron.decline.given

/**
 * Configuration for the output data cutter that trims flight data to the jump-relevant portion.
 *
 * @param keepBeforeTakeoff Number of data points to retain before the detected takeoff
 * @param keepAfterLanding Number of data points to retain after the detected landing
 * @param minRetainedPoints Minimum fraction of total points that must be retained; below this the file is kept uncut
 */
final case class DataCutterConfig(
  keepBeforeTakeoff: KeepPoints,
  keepAfterLanding: KeepPoints,
  minRetainedPoints: Percent,
)

object DataCutterConfig {

  val default: DataCutterConfig =
    DataCutterConfig(
      keepBeforeTakeoff = 500,
      keepAfterLanding = 500,
      minRetainedPoints = 0.1,
    )

  lazy val declineOpts: Opts[DataCutterConfig] =
    (keepBeforeTakeoff, keepAfterLanding, minRetainedPoints).mapN(DataCutterConfig.apply)

  private lazy val keepBeforeTakeoff: Opts[KeepPoints] =
    Opts
      .option[Int :| GreaterEqual[0]](
        long = "keep-before-takeoff",
        help = "Number of points to keep before a takeoff has been detected",
      )
      .map(KeepPoints.apply)
      .withDefault(default.keepBeforeTakeoff)

  private lazy val keepAfterLanding: Opts[KeepPoints] =
    Opts
      .option[Int :| GreaterEqual[0]](
        long = "keep-after-landing",
        help = "Number of points to keep after a landing has been detected",
      )
      .map(KeepPoints.apply)
      .withDefault(default.keepAfterLanding)

  private lazy val minRetainedPoints: Opts[Percent] =
    Opts
      .option[Double :| Interval.Closed[0.0, 1.0]](
        long = "min-retain-points",
        help = "Percentage of minimum point the tool must keep.",
      )
      .map(Percent.apply)
      .withDefault(default.minRetainedPoints)

  given Conversion[Int, KeepPoints] with
    def apply(x: Int): KeepPoints = KeepPoints(x.refineUnsafe)

  given Conversion[Double, Percent] with
    def apply(x: Double): Percent = Percent(x.refineUnsafe)

}
