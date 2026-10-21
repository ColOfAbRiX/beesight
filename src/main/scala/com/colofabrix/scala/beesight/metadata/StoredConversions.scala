package com.colofabrix.scala.beesight.metadata

import com.colofabrix.scala.beesight.detection.config.{ Altitude, Angle, Speed }
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.output.DataCutter
import java.time.Duration

/**
 * Bidirectional conversions between detection-library models and their JSON-storage counterparts.
 */
object StoredConversions {

  extension (point: EventPoint)
    def toStored: StoredPoint =
      StoredPoint(
        index = point.index,
        time = point.time,
        altitude = point.altitude,
        speedNorth = point.speed.north,
        speedEast = point.speed.east,
        speedVertical = point.speed.vertical,
      )

  extension (point: StoredPoint)
    def toEventPoint: EventPoint =
      EventPoint(
        index = point.index,
        altitude = point.altitude,
        time = point.time,
        speed = GeoVector(point.speedNorth, point.speedEast, point.speedVertical),
      )

  extension (events: FlightEvents)
    def toStored: StoredFlightEvents =
      StoredFlightEvents(
        takeoff = events.takeoff.map(_.toStored),
        freefall = events.freefall.map(_.toStored),
        canopy = events.canopy.map(_.toStored),
        landing = events.landing.map(_.toStored),
      )

  extension (events: StoredFlightEvents)
    def toFlightEvents: FlightEvents =
      FlightEvents(
        takeoff = events.takeoff.map(_.toEventPoint),
        freefall = events.freefall.map(_.toEventPoint),
        canopy = events.canopy.map(_.toEventPoint),
        landing = events.landing.map(_.toEventPoint),
      )

  extension (swoop: SwoopData)
    def toStored: StoredSwoopData =
      StoredSwoopData(
        turnInitiation = swoop.turnInitiation.toStored,
        rolloutStart = swoop.rolloutStart.toStored,
        maxVerticalSpeed = swoop.maxVerticalSpeed.toStored,
        maxTotalSpeed = swoop.maxTotalSpeed.toStored,
        maxHorizontalSpeed = swoop.maxHorizontalSpeed.toStored,
        degreesOfRotation = swoop.degreesOfRotation.value,
        turnDirection = swoop.turnDirection.productPrefix,
        timeToExecuteTurnMillis = swoop.timeToExecuteTurn.toMillis,
        ground = swoop.ground.value,
        patternEntry = swoop.patternEntry.toStored,
        divePoint = swoop.divePoint.toStored,
        stop = swoop.stop.toStored,
        bottomOfTurn = swoop.bottomOfTurn.toStored,
      )

  extension (swoop: StoredSwoopData)
    def toSwoopData: Either[String, SwoopData] =
      turnDirection(swoop.turnDirection).map { direction =>
        SwoopData(
          turnInitiation = swoop.turnInitiation.toEventPoint,
          rolloutStart = swoop.rolloutStart.toEventPoint,
          maxVerticalSpeed = swoop.maxVerticalSpeed.toSpeedAtAltitude,
          maxTotalSpeed = swoop.maxTotalSpeed.toSpeedAtAltitude,
          maxHorizontalSpeed = swoop.maxHorizontalSpeed.toSpeedAtAltitude,
          degreesOfRotation = Angle(swoop.degreesOfRotation),
          turnDirection = direction,
          timeToExecuteTurn = Duration.ofMillis(swoop.timeToExecuteTurnMillis),
          ground = Altitude(swoop.ground),
          patternEntry = swoop.patternEntry.toEventPoint,
          divePoint = swoop.divePoint.toEventPoint,
          stop = swoop.stop.toEventPoint,
          bottomOfTurn = swoop.bottomOfTurn.toEventPoint,
        )
      }

  extension (summary: DataCutter.CutSummary)
    def toStored: StoredCutSummary =
      StoredCutSummary(
        applied = summary.applied,
        totalPoints = summary.totalPoints,
        retainedPoints = summary.retainedPoints,
        firstRetainedIndex = summary.firstRetainedIndex,
        lastRetainedIndex = summary.lastRetainedIndex,
      )

  private def turnDirection(name: String): Either[String, TurnDirection] =
    name.toLowerCase() match {
      case "left"  => Right(TurnDirection.Left)
      case "right" => Right(TurnDirection.Right)
      case other   => Left(s"Unknown turn direction: $other")
    }

  extension (speed: SpeedAtAltitude)
    private def toStored: StoredSpeedAtAltitude =
      StoredSpeedAtAltitude(idx = speed.idx, speed = speed.speed.value, altitude = speed.altitude.value)

  extension (speed: StoredSpeedAtAltitude)
    private def toSpeedAtAltitude: SpeedAtAltitude =
      SpeedAtAltitude(idx = speed.idx, speed = Speed(speed.speed), altitude = Altitude(speed.altitude))

}
