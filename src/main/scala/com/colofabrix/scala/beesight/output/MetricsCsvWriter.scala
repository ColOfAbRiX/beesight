package com.colofabrix.scala.beesight.output

import cats.data.NonEmptyList
import com.colofabrix.scala.beesight.IOConfig
import com.colofabrix.scala.beesight.config.Config
import com.colofabrix.scala.beesight.detection.model.FlightMetric
import com.colofabrix.scala.beesight.pipeline.Stage
import fs2.data.csv.*
import fs2.io.file.{ Files, Path as Fs2Path }
import java.nio.file.Path

/**
 * Writes the per-point flight metrics to CSV files with a fixed header contract.
 */
final private[beesight] class MetricsCsvWriter private (config: Config) {

  /** The CSV header, fixed by contract */
  val header: List[String] =
    MetricsCsvWriter.header

  /** Renders one metric positionally over the header, with missing values as empty cells */
  def row(metric: FlightMetric): List[String] =
    MetricsCsvWriter.row(metric)

  /**
    * Returns a pipe that writes the metrics of each point to a CSV file when the Metrics stage is selected
    */
  def writePipe(filePath: Path)(using CsvRowEncoder[FlightMetric, String]): fs2.Pipe[IOConfig, (?, FlightMetric), Nothing] =
    stream =>
      if !config.global.dryRun && config.command.stages.contains(Stage.Metrics) then
        stream
          .map(_._2)
          .through(encodeUsingFirstHeaders(fullRows = true))
          .through(fs2.text.utf8.encode)
          .through(Files[IOConfig].writeAll(Fs2Path.fromNioPath(filePath)))
          .drain
      else
        stream.drain

}

private[beesight] object MetricsCsvWriter {

  def apply(config: Config): MetricsCsvWriter =
    new MetricsCsvWriter(config)

  val header: List[String] =
    List(
      "time",
      "altitude",
      "vertical_speed",
      "horizontal_speed",
      "total_speed",
      "course",
      "course_rate",
      "course_accuracy",
      "course_accuracy_estimate",
      "glide_ratio",
      "lift_coefficient",
      "drag_coefficient",
      "dive_angle",
      "dive_rate",
      "turn_rate",
      "speed_accuracy",
      "horizontal_accuracy",
      "vertical_accuracy",
      "satellites",
      "acceleration_north",
      "acceleration_east",
      "acceleration_vertical",
      "acceleration_magnitude",
      "acceleration_along_track",
      "acceleration_cross_track",
      "total_energy",
      "energy_rate",
      "spherical_error_probable",
      "speed_score_accuracy",
    )

  def row(metric: FlightMetric): List[String] =
    List(
      metric.time.toString,
      metric.altitude.toString,
      metric.verticalSpeed.toString,
      metric.horizontalSpeed.toString,
      metric.totalSpeed.toString,
      metric.course.toString,
      metric.courseRate.toString,
      render(metric.courseAccuracy),
      render(metric.courseAccuracyEstimate),
      metric.glideRatio.toString,
      metric.liftCoefficient.toString,
      metric.dragCoefficient.toString,
      metric.diveAngle.toString,
      metric.diveRate.toString,
      metric.turnRate.toString,
      render(metric.speedAccuracy),
      render(metric.horizontalAccuracy),
      render(metric.verticalAccuracy),
      render(metric.satellites),
      metric.acceleration.north.toString,
      metric.acceleration.east.toString,
      metric.acceleration.vertical.toString,
      metric.accelerationMagnitude.toString,
      metric.accelerationAlongTrack.toString,
      metric.accelerationCrossTrack.toString,
      metric.totalEnergy.toString,
      metric.energyRate.toString,
      render(metric.sphericalErrorProbably),
      render(metric.speedScoreAccuracy),
    )

  given csvRowEncoder: CsvRowEncoder[FlightMetric, String] with {

    def apply(metric: FlightMetric): CsvRow[String] =
      CsvRow.fromNelHeaders(NonEmptyList.fromListUnsafe(header.zip(row(metric))))

  }

  private def render[A](value: Option[A]): String =
    value.fold("")(_.toString)

}
