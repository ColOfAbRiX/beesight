package com.colofabrix.scala.beesight.detection.metrics

import fs2.data.csv.*
import java.time.Instant

/**
 * One row of the golden metrics export, used as the reference for the bulk comparison.
 *
 * The export lists its plotted values in display units: elevations and accuracies in feet, speeds in mph, angles in
 * degrees with the course in (-180, 180]. The comparison converts them to the SI units used by [[FlightMetric]].
 *
 * The `Acceleration (m/s^2)` column (the slope of the total speed) has no counterpart in `FlightMetric` and is not
 * loaded.
 */
final private[beesight] case class FlySightMetricsResult(
  time: Instant,
  elevation: Option[Double],       // ft
  verticalSpeed: Option[Double],   // mph
  horizontalSpeed: Option[Double], // mph
  totalSpeed: Option[Double],      // mph
  diveAngle: Option[Double],       // deg
  diveRate: Option[Double],        // deg/s
  glideRatio: Option[Double],
  horizontalAccuracy: Option[Double], // ft
  verticalAccuracy: Option[Double],   // ft
  speedAccuracy: Option[Double],      // mph
  satellites: Option[Int],
  totalEnergy: Option[Double], // J/kg
  energyRate: Option[Double],  // J/kg/s
  liftCoefficient: Option[Double],
  dragCoefficient: Option[Double],
  course: Option[Double],                 // deg, in (-180, 180]
  courseRate: Option[Double],             // deg/s
  courseAccuracy: Option[Double],         // rad: dimensionless sAcc/|v|, despite the export's "(deg)" header
  accelerationForward: Option[Double],    // m/s²
  accelerationRight: Option[Double],      // m/s²
  accelerationDown: Option[Double],       // m/s²
  accelerationMagnitude: Option[Double],  // m/s²
  sphericalErrorProbable: Option[Double], // m
  speedScoreAccuracy: Option[Double],     // m/s
)

private[beesight] object FlySightMetricsResult {

  given CsvRowDecoder[FlySightMetricsResult, String] with
    def apply(row: CsvRow[String]): DecoderResult[FlySightMetricsResult] =
      for
        time                   <- row.as[String]("Time (UTC)").flatMap(parseInstant)
        elevation              <- optDouble(row, "Elevation (ft)")
        verticalSpeed          <- optDouble(row, "Vertical Speed (mph)")
        horizontalSpeed        <- optDouble(row, "Horizontal Speed (mph)")
        totalSpeed             <- optDouble(row, "Total Speed (mph)")
        diveAngle              <- optDouble(row, "Dive Angle (deg)")
        diveRate               <- optDouble(row, "Dive Rate (deg/s)")
        glideRatio             <- optDouble(row, "Glide Ratio")
        horizontalAccuracy     <- optDouble(row, "Horizontal Accuracy (ft)")
        verticalAccuracy       <- optDouble(row, "Vertical Accuracy (ft)")
        speedAccuracy          <- optDouble(row, "Speed Accuracy (mph)")
        satellites             <- optInt(row, "Number of Satellites")
        totalEnergy            <- optDouble(row, "Total Energy (J/kg)")
        energyRate             <- optDouble(row, "Energy Rate (J/kg/s)")
        liftCoefficient        <- optDouble(row, "Lift Coefficient")
        dragCoefficient        <- optDouble(row, "Drag Coefficient")
        course                 <- optDouble(row, "Course (deg)")
        courseRate             <- optDouble(row, "Course Rate (deg/s)")
        courseAccuracy         <- optDouble(row, "Course Accuracy (deg)")
        accelerationForward    <- optDouble(row, "Acceleration Forward (m/s^2)")
        accelerationRight      <- optDouble(row, "Acceleration Right (m/s^2)")
        accelerationDown       <- optDouble(row, "Acceleration Down (m/s^2)")
        accelerationMagnitude  <- optDouble(row, "Acceleration Magnitude (m/s^2)")
        sphericalErrorProbable <- optDouble(row, "Spherical Error Probable (m)")
        speedScoreAccuracy     <- optDouble(row, "Speed Score Accuracy (m/s)")
      yield FlySightMetricsResult(
        time = time,
        elevation = elevation,
        verticalSpeed = verticalSpeed,
        horizontalSpeed = horizontalSpeed,
        totalSpeed = totalSpeed,
        diveAngle = diveAngle,
        diveRate = diveRate,
        glideRatio = glideRatio,
        horizontalAccuracy = horizontalAccuracy,
        verticalAccuracy = verticalAccuracy,
        speedAccuracy = speedAccuracy,
        satellites = satellites,
        totalEnergy = totalEnergy,
        energyRate = energyRate,
        liftCoefficient = liftCoefficient,
        dragCoefficient = dragCoefficient,
        course = course,
        courseRate = courseRate,
        courseAccuracy = courseAccuracy,
        accelerationForward = accelerationForward,
        accelerationRight = accelerationRight,
        accelerationDown = accelerationDown,
        accelerationMagnitude = accelerationMagnitude,
        sphericalErrorProbable = sphericalErrorProbable,
        speedScoreAccuracy = speedScoreAccuracy,
      )

  private def parseInstant(s: String): DecoderResult[Instant] =
    try Right(Instant.parse(s.trim))
    catch case t: Exception => Left(new DecoderError(t.getMessage))

  /** The export writes `nan` for the values it could not compute; they compare as absent. */
  private def optDouble(row: CsvRow[String], name: String): DecoderResult[Option[Double]] =
    row.as[String](name).map { s =>
      val trimmed = s.trim
      if trimmed.isEmpty || trimmed.equalsIgnoreCase("nan") then None else Some(trimmed.toDouble)
    }

  private def optInt(row: CsvRow[String], name: String): DecoderResult[Option[Int]] =
    optDouble(row, name).map(_.map(_.toInt))

}

/** One row of the index file pairing a recording with its golden metrics file. */
final private[beesight] case class MetricsValidationPair(
  inputFile: String,
  metricsFile: String,
  groundElevationResidual: Option[Double] = None,
)

private[beesight] object MetricsValidationPair {

  given CsvRowDecoder[MetricsValidationPair, String] with
    def apply(row: CsvRow[String]): DecoderResult[MetricsValidationPair] =
      for
        inputFile               <- row.as[String]("input_file")
        metricsFile             <- row.as[String]("metrics_file")
        groundElevationResidual <- optDouble(row, "ground_elevation_residual")
      yield MetricsValidationPair(
        inputFile = inputFile.trim,
        metricsFile = metricsFile.trim,
        groundElevationResidual = groundElevationResidual,
      )

  private def optDouble(row: CsvRow[String], name: String): DecoderResult[Option[Double]] =
    row.as[String](name).map { s =>
      val trimmed = s.trim
      if trimmed.isEmpty || trimmed.equalsIgnoreCase("nan") then None else Some(trimmed.toDouble)
    }

}
