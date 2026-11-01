package com.colofabrix.scala.beesight.pipeline

import cats.syntax.all.*
import com.colofabrix.scala.beesight.IOConfig
import com.colofabrix.scala.beesight.config.Config
import com.colofabrix.scala.beesight.detection.jumpevents.JumpEventsDetector
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.detection.readers.*
import com.colofabrix.scala.beesight.metadata.*
import com.colofabrix.scala.beesight.metadata.ConfigHash.*
import com.colofabrix.scala.beesight.metadata.StoredConversions.*

/**
 * Runs the jump-events detection stage, reusing the cached detection when the metadata file section is fresh.
 */
object JumpEventsStage {

  final case class DetectedEvents[A](
    rows: Vector[(FlightEvents, A)],
    finalEvents: FlightEvents,
    configHash: ConfigHash,
    storedEvents: StoredFlightEvents,
  )

  def run[A: PointReader](points: Vector[A], metadataFile: MetadataFile): IOConfig[DetectedEvents[A]] =
    IOConfig
      .ask
      .flatMap { config =>
        val configHash = config.configHash

        metadataFile.jumpevents
          .filter(_ => metadataFile.configHash === configHash)
          .fold(runDetection[A](config, points, configHash)) { events =>
            loadFromMetadataFile[A](events, points, configHash).pure[IOConfig]
          }
      }

  private def runDetection[A: PointReader](
    cfg: Config,
    points: Vector[A],
    configHash: ConfigHash,
  ): IOConfig[DetectedEvents[A]] =
    val detector    = JumpEventsDetector(cfg.detectionConfig.jumpevents)
    val pointStream = fs2.Stream.emits[IOConfig, A](points)

    (pointStream.through(detector.detect) zip pointStream)
      .compile
      .toVector
      .map { zipped =>
        val finalEvents = zipped.lastOption.fold(FlightEvents.empty)(_._1)
        DetectedEvents(zipped, finalEvents, configHash, finalEvents.toStored)
      }

  private def loadFromMetadataFile[A](
    events: StoredFlightEvents,
    points: Vector[A],
    configHash: ConfigHash,
  ): DetectedEvents[A] =
    val detected = events.toFlightEvents
    val rows     =
      points
        .zipWithIndex
        .map {
          case (point, index) =>
            (flightEventsAt(detected, index.toLong), point)
        }

    DetectedEvents(rows, detected, configHash, events)

  private def flightEventsAt(events: FlightEvents, index: Long): FlightEvents =
    FlightEvents(
      takeoff = events.takeoff.filter(_.index <= index),
      freefall = events.freefall.filter(_.index <= index),
      canopy = events.canopy.filter(_.index <= index),
      landing = events.landing.filter(_.index <= index),
    )

}
