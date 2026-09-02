package com.colofabrix.scala.beesight.swoop

import java.nio.file.Path
import scala.io.Source

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

  /** Loads the expected values of all recordings from the summary CSV. */
  def loadAll(path: Path): List[GSwoopResult] =
    val lines =
      Source
        .fromFile(path.toFile)
        .getLines()
        .toList
        .filterNot(_.isBlank)

    val header =
      lines.head
        .split(",")
        .zipWithIndex
        .toMap

    lines
      .tail
      .filter(_.trim.nonEmpty)
      .map(parse(header, _))

  private def parse(header: Map[String, Int], line: String): GSwoopResult =
    val cols = line.split(",", -1)

    GSwoopResult(
      filename = cols(header("filename")),
      turnInitiationAltitude = parseOptDouble(cols.lift(header("turn_initiation_altitude"))),
      rolloutStartAltitude = parseOptDouble(cols.lift(header("rollout_start_altitude"))),
      finishedRolloutAltitude = parseOptDouble(cols.lift(header("finished_rollout_altitude"))),
      maxVerticalSpeed = parseOptDouble(cols.lift(header("max_vertical_speed"))),
      maxTotalSpeed = parseOptDouble(cols.lift(header("max_total_speed"))),
      maxHorizontalSpeed = parseOptDouble(cols.lift(header("max_horizontal_speed"))),
      degreesOfRotation = parseOptDouble(cols.lift(header("degrees_of_rotation"))),
      timeToExecuteTurn = parseOptDouble(cols.lift(header("time_to_execute_turn"))),
      timeDuringRollout = parseOptDouble(cols.lift(header("time_during_rollout"))),
      timeAloftDuringSwoop = parseOptDouble(cols.lift(header("time_aloft_during_swoop"))),
      entryGateSpeed = parseOptDouble(cols.lift(header("entry_gate_speed"))),
      distanceToStop = parseOptDouble(cols.lift(header("distance_to_stop"))),
      touchdownEstimate = parseOptDouble(cols.lift(header("touchdown_estimate"))),
    )

  private def parseOptDouble(value: Option[String]): Option[Double] =
    value.filter(_.trim.nonEmpty).map(_.trim.toDouble)

}
