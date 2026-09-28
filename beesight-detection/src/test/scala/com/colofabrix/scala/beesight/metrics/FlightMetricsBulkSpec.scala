package com.colofabrix.scala.beesight.metrics

import cats.effect.IO
import cats.syntax.all.given
import com.colofabrix.scala.beesight.IOValues
import com.colofabrix.scala.beesight.TestFilesHandler
import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.jumpevents.JumpEventsDetector
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.readers.*
import fs2.data.csv.*
import java.nio.file.Path
import java.time.format.DateTimeFormatter
import java.time.{ Instant, OffsetDateTime }
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.concurrent.duration.*

/**
 * Bulk check of the computed flight metrics against the golden metrics export.
 *
 * Each row of `metrics_results.csv` pairs a recording with its golden metrics file; the metrics computed by
 * [[FlightMetricsCalculator]] are compared field by field, after converting the export's display units
 * (see [[MetricsMatchers]]).
 */
class FlightMetricsBulkSpec extends AnyWordSpec with Matchers with IOValues with MetricsMatchers {

  private val testFilesDir = TestFilesHandler.testFilesDir

  private val resultsFile = testFilesDir.resolve("metrics_results.csv")

  "FlightMetricsCalculator" should {

    "match the golden metrics of the validation recordings" in {
      val pairs =
        TestFilesHandler
          .readCsv[MetricsValidationPair](resultsFile)
          .compile
          .toList
          .result()

      pairs should not be empty

      val fileResults =
        pairs
          .zipWithIndex
          .parFlatTraverse { case (pair, idx) => validatePair(pair, idx).map(List(_)) }
          .result(timeout = 5.minutes)

      val total       = fileResults.size
      val failed      = fileResults.count(_.nonEmpty)
      val successRate = 1.0 - (failed.toDouble / total.toDouble)

      info(f"Success rate is ${successRate * 100.0}%.1f%%")
      info(f"    Total number of files: $total")
      info(f"    Failed files:          $failed\n")
      if fileResults.exists(_.nonEmpty) then
        info(s"Failing tests summary:\n${fileResults.filter(_.nonEmpty).map(_.mkString("\n")).mkString("\n\n")}")

      successRate shouldBe >=(1.0)
    }

  }

  private def validatePair(pair: MetricsValidationPair, idx: Int): IO[List[String]] =
    for
      obtained <- computeMetrics(testFilesDir.resolve(pair.inputFile), pair.groundElevationResidual)
      golden   <- TestFilesHandler.readCsv[FlySightMetricsResult](testFilesDir.resolve(pair.metricsFile)).compile.toList
    yield
      val failures = compareWithGolden(obtained, golden)
      if failures.isEmpty then Nil else s"[$idx] ${pair.inputFile}:" :: failures

  private def computeMetrics(inputPath: Path, residual: Option[Double] = None): IO[List[FlightMetric]] =
    for
      events         <- detectEvents(inputPath)
      config          = DetectionConfig.default.metrics
      detectedGround  = MetricsGround.detect(events.landing, config)
      groundElevation = detectedGround + residual.getOrElse(0.0)
      metrics        <- TestFilesHandler
                   .readCsv[GpsCsvPoint](inputPath)
                   .through(FlightMetricsCalculator(config, groundElevation).compute[IO, GpsCsvPoint])
                   .compile
                   .toList
    yield metrics

  private def detectEvents(inputPath: Path): IO[FlightEvents] =
    TestFilesHandler
      .readCsv[GpsCsvPoint](inputPath)
      .through(JumpEventsDetector(DetectionConfig.default.jumpevents).detect)
      .compile
      .last
      .map(_.getOrElse(FlightEvents.empty))

  /**
   * Aligns the computed metrics with the golden rows by timestamp and summarises the mismatches per field: one line
   * per field with the number of mismatching points and a sample message.
   */
  private def compareWithGolden(obtained: List[FlightMetric], golden: List[FlySightMetricsResult]): List[String] =
    val structural =
      if obtained.isEmpty then List("  no input points were decoded")
      else if golden.isEmpty then List("  no golden metrics rows were decoded")
      else if obtained.size != golden.size then
        List(s"  row count mismatch: ${obtained.size} computed, ${golden.size} golden")
      else Nil

    val goldenByTime = golden.groupMap(_.time)(identity)

    val mismatches = obtained.flatMap { metric =>
      goldenByTime.get(metric.time) match
        case None       => Nil
        case Some(rows) =>
          rows.headOption
            .map(row => checkFlightMetric(metric, row))
            .getOrElse(Nil)
            .map(msg => fieldName(msg) -> msg)
    }

    val missing = obtained.count(metric => !goldenByTime.contains(metric.time))

    val summary = mismatches
      .groupMap(_._1)(_._2)
      .toList
      .sortBy(_._1)
      .map {
        case (field, messages) =>
          s"  $field: ${messages.size}/${obtained.size} points mismatched; e.g.${messages.head}"
      }

    structural ++ Option.when(missing > 0)(s"  $missing computed metrics have no golden row").toList ++ summary

  private def fieldName(message: String): String =
    message.trim.takeWhile(_ != ':')

}

/**
 * A raw flight data point decoded from the test CSV files, usable as metrics input via its [[PointReader]] and
 * [[GpsReader]].
 */
final private[beesight] case class GpsCsvPoint(
  time: Instant,
  altitude: Double,
  speed: GeoVector,
  horizontalAccuracy: Option[Double],
  verticalAccuracy: Option[Double],
  speedAccuracy: Option[Double],
  courseAccuracy: Option[Double],
  satellites: Option[Int],
)

private object GpsCsvPoint {

  given PointReader[GpsCsvPoint] with {
    def time(point: GpsCsvPoint): Instant    = point.time
    def altitude(point: GpsCsvPoint): Double = point.altitude
    def speed(point: GpsCsvPoint): GeoVector = point.speed
  }

  given GpsReader[GpsCsvPoint] with {
    def horizontalAccuracy(point: GpsCsvPoint): Option[Double] = point.horizontalAccuracy
    def verticalAccuracy(point: GpsCsvPoint): Option[Double]   = point.verticalAccuracy
    def speedAccuracy(point: GpsCsvPoint): Option[Double]      = point.speedAccuracy
    def courseAccuracy(point: GpsCsvPoint): Option[Double]     = point.courseAccuracy
    def satellites(point: GpsCsvPoint): Option[Int]            = point.satellites
  }

  private given CellDecoder[OffsetDateTime] =
    CellDecoder[String].emap { value =>
      try Right(OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME))
      catch case t: Exception => Left(new DecoderError(t.getMessage))
    }

  given CsvRowDecoder[GpsCsvPoint, String] with
    def apply(row: CsvRow[String]): DecoderResult[GpsCsvPoint] =
      for
        time  <- row.as[OffsetDateTime]("time")
        hMSL  <- row.as[Double]("hMSL")
        velN  <- row.as[Double]("velN")
        velE  <- row.as[Double]("velE")
        velD  <- row.as[Double]("velD")
        hAcc  <- optDouble(row, "hAcc")
        vAcc  <- optDouble(row, "vAcc")
        sAcc  <- optDouble(row, "sAcc")
        cAcc  <- optDouble(row, "cAcc")
        numSV <- optInt(row, "numSV")
      yield GpsCsvPoint(
        time = time.toInstant,
        altitude = hMSL,
        speed = GeoVector(north = velN, east = velE, vertical = velD),
        horizontalAccuracy = hAcc,
        verticalAccuracy = vAcc,
        speedAccuracy = sAcc,
        courseAccuracy = cAcc,
        satellites = numSV,
      )

  private def optDouble(row: CsvRow[String], name: String): DecoderResult[Option[Double]] =
    row.as[String](name).map { s =>
      val trimmed = s.trim
      if trimmed.isEmpty || trimmed.equalsIgnoreCase("nan") then None else Some(trimmed.toDouble)
    }

  private def optInt(row: CsvRow[String], name: String): DecoderResult[Option[Int]] =
    optDouble(row, name).map(_.map(_.toInt))

}
