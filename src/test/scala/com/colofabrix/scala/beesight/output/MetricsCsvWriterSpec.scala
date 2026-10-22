package com.colofabrix.scala.beesight.output

import com.colofabrix.scala.beesight.*
import com.colofabrix.scala.beesight.IOConfigValues
import com.colofabrix.scala.beesight.config.{ Command, Config }
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.output.MetricsCsvWriter.given
import fs2.data.csv.CsvRowEncoder
import fs2.io.file.{ Files, Path as Fs2Path }
import java.nio.file.{ Files as JFiles, Path }
import java.time.Instant
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class MetricsCsvWriterSpec extends AnyFreeSpec with Matchers with IOConfigValues {

  "MetricsCsvWriter" - {

    "exposes the fixed header contract" in {
      val expected = List(
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

      val actual = writer.header

      actual shouldBe expected
    }

    "renders a complete metric positionally" in {
      val expected = List(
        "2026-01-15T10:00:00Z",
        "1200.5",
        "40.0",
        "30.0",
        "50.0",
        "315.0",
        "12.5",
        "1.5",
        "2.5",
        "0.75",
        "0.4",
        "0.1",
        "45.0",
        "-3.5",
        "90.0",
        "0.5",
        "1.0",
        "1.5",
        "14",
        "1.5",
        "-2.5",
        "9.8",
        "10.4",
        "9.5",
        "0.5",
        "2500.0",
        "-125.0",
        "3.5",
        "0.25",
      )

      val actual = writer.row(metric)

      actual shouldBe expected
    }

    "renders missing values as empty cells" in {
      val bare = metric.copy(
        courseAccuracy = None,
        courseAccuracyEstimate = None,
        speedAccuracy = None,
        horizontalAccuracy = None,
        verticalAccuracy = None,
        satellites = None,
        sphericalErrorProbably = None,
        speedScoreAccuracy = None,
      )

      val actual = writer.row(bare)

      actual should have size writer.header.size
      List(7, 8, 15, 16, 17, 18, 27, 28).map(actual) should contain only ""
    }

    "encodes rows over the contract header" in {
      val row = summon[CsvRowEncoder[FlightMetric, String]].apply(metric)

      row.headers.fold(List.empty[String])(_.toList) shouldBe writer.header
      row.values.toList shouldBe writer.row(metric)
    }

    "writes the metrics CSV when the Metrics stage is selected" in {
      val config = Config.default.copy(command = Command.Metrics)
      val file   = tempFile("metrics.csv")

      metricsStream(
        events,
        metric
      ).through(MetricsCsvWriter(config).writePipe(file.toNioPath)).compile.drain.result(config)

      val content = readFile(file).result(config)

      content.trim.linesIterator.toList shouldBe List(
        writer.header.mkString(","),
        writer.row(metric).mkString(","),
      )
    }

    "writes nothing on a dry run" in {
      val config = Config.default.copy(
        command = Command.Metrics,
        global = Config.default.global.copy(dryRun = true),
      )
      val file = tempFile("metrics_dry.csv")

      metricsStream(
        events,
        metric
      ).through(MetricsCsvWriter(config).writePipe(file.toNioPath)).compile.drain.result(config)

      JFiles.exists(file.toNioPath) shouldBe false
    }

    "writes nothing when the Metrics stage is not selected" in {
      val config = Config.default.copy(command = Command.JumpEvents)
      val file   = tempFile("metrics_other_stage.csv")

      metricsStream(
        events,
        metric
      ).through(MetricsCsvWriter(config).writePipe(file.toNioPath)).compile.drain.result(config)

      JFiles.exists(file.toNioPath) shouldBe false
    }

  }

  private lazy val metric =
    FlightMetric(
      time = Instant.parse("2026-01-15T10:00:00Z"),
      altitude = 1200.5,
      verticalSpeed = 40.0,
      horizontalSpeed = 30.0,
      totalSpeed = 50.0,
      course = 315.0,
      courseRate = 12.5,
      courseAccuracy = Some(1.5),
      courseAccuracyEstimate = Some(2.5),
      glideRatio = 0.75,
      liftCoefficient = 0.4,
      dragCoefficient = 0.1,
      diveAngle = 45.0,
      diveRate = -3.5,
      turnRate = 90.0,
      speedAccuracy = Some(0.5),
      horizontalAccuracy = Some(1.0),
      verticalAccuracy = Some(1.5),
      satellites = Some(14),
      acceleration = GeoVector(1.5, -2.5, 9.8),
      accelerationMagnitude = 10.4,
      accelerationAlongTrack = 9.5,
      accelerationCrossTrack = 0.5,
      totalEnergy = 2500.0,
      energyRate = -125.0,
      sphericalErrorProbably = Some(3.5),
      speedScoreAccuracy = Some(0.25),
    )

  private lazy val events = FlightEvents.empty
  private lazy val writer = MetricsCsvWriter(Config.default)

  private def metricsStream(events: FlightEvents, metric: FlightMetric) =
    fs2.Stream.emits[IOConfig, (FlightEvents, FlightMetric)](List(events -> metric))

  private def tempFile(name: String): Fs2Path =
    Fs2Path.fromNioPath(JFiles.createTempDirectory("metrics_csv_writer_spec").resolve(name))

  private def readFile(file: Fs2Path) =
    Files[IOConfig].readAll(file).through(fs2.text.utf8.decode).compile.string

}
