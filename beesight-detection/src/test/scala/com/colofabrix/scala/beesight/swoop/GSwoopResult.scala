package com.colofabrix.scala.beesight.swoop

import fs2.data.csv.*

final private[beesight] case class GSwoopResult(
  filename: String,
  turnInitiationAltitude: Option[Double],
  rolloutStartAltitude: Option[Double],
  finishedRolloutAltitude: Option[Double],
  maxVerticalSpeed: Option[Double],
  maxTotalSpeed: Option[Double],
  maxHorizontalSpeed: Option[Double],
  degreesOfRotation: Option[Double],
  timeToExecuteTurn: Option[Double],
  timeDuringRollout: Option[Double],
  timeAloftDuringSwoop: Option[Double],
  entryGateSpeed: Option[Double],
  distanceToStop: Option[Double],
  touchdownEstimate: Option[Double],
) {

  def isEmpty: Boolean =
    turnInitiationAltitude.isEmpty &&
    rolloutStartAltitude.isEmpty &&
    finishedRolloutAltitude.isEmpty &&
    maxVerticalSpeed.isEmpty &&
    maxTotalSpeed.isEmpty &&
    maxHorizontalSpeed.isEmpty &&
    degreesOfRotation.isEmpty &&
    timeToExecuteTurn.isEmpty &&
    timeDuringRollout.isEmpty &&
    timeAloftDuringSwoop.isEmpty &&
    entryGateSpeed.isEmpty &&
    distanceToStop.isEmpty &&
    touchdownEstimate.isEmpty

}

private[beesight] object GSwoopResult {

  given CsvRowDecoder[GSwoopResult, String] with
    def apply(row: CsvRow[String]): DecoderResult[GSwoopResult] =
      for
        filename                    <- row.as[String]("filename")
        turnInitiationAltitude      <- optDouble(row, "turn_initiation_altitude")
        rolloutStartAltitude        <- optDouble(row, "rollout_start_altitude")
        finishedRolloutAltitude     <- optDouble(row, "finished_rollout_altitude")
        maxVerticalSpeed            <- optDouble(row, "max_vertical_speed")
        maxTotalSpeed               <- optDouble(row, "max_total_speed")
        maxHorizontalSpeed          <- optDouble(row, "max_horizontal_speed")
        degreesOfRotation           <- optDouble(row, "degrees_of_rotation")
        timeToExecuteTurn           <- optDouble(row, "time_to_execute_turn")
        timeDuringRollout           <- optDouble(row, "time_during_rollout")
        timeAloftDuringSwoop        <- optDouble(row, "time_aloft_during_swoop")
        entryGateSpeed              <- optDouble(row, "entry_gate_speed")
        distanceToStop              <- optDouble(row, "distance_to_stop")
        touchdownEstimate           <- optDouble(row, "touchdown_estimate")
      yield GSwoopResult(
        filename = filename,
        turnInitiationAltitude = turnInitiationAltitude,
        rolloutStartAltitude = rolloutStartAltitude,
        finishedRolloutAltitude = finishedRolloutAltitude,
        maxVerticalSpeed = maxVerticalSpeed,
        maxTotalSpeed = maxTotalSpeed,
        maxHorizontalSpeed = maxHorizontalSpeed,
        degreesOfRotation = degreesOfRotation,
        timeToExecuteTurn = timeToExecuteTurn,
        timeDuringRollout = timeDuringRollout,
        timeAloftDuringSwoop = timeAloftDuringSwoop,
        entryGateSpeed = entryGateSpeed,
        distanceToStop = distanceToStop,
        touchdownEstimate = touchdownEstimate,
      )

  private def optDouble(row: CsvRow[String], name: String): DecoderResult[Option[Double]] =
    row.as[String](name).map { s =>
      val trimmed = s.trim
      if trimmed.isEmpty then None else Some(trimmed.toDouble)
    }

}
