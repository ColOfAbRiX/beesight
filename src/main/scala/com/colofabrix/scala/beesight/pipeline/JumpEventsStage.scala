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
 * Runs the jump-events detection stage, reusing the cached detection when the sidecar section is fresh.
 */
object JumpEventsStage {

  final case class Result[A](
    rows: Vector[(FlightEvents, A)],
    finalEvents: FlightEvents,
    section: JumpEventsSection,
  )

  def run[A: PointReader](points: Vector[A], sidecar: Sidecar): IOConfig[Result[A]] =
    IOConfig
      .ask
      .flatMap { config =>
        val configHash = config.detectionConfig.jumpevents.configHash

        sidecar
          .sections
          .jumpevents
          .filter(_.configHash === configHash)
          .fold(runDetection[A](config, points, configHash)) { section =>
            loadFromSidecar[A](section, points).pure[IOConfig]
          }
      }

  private def runDetection[A: PointReader](
    cfg: Config,
    points: Vector[A],
    configHash: ConfigHash,
  ): IOConfig[Result[A]] =
    val detector    = JumpEventsDetector(cfg.detectionConfig.jumpevents)
    val pointStream = fs2.Stream.emits[IOConfig, A](points)

    (pointStream.through(detector.detect) zip pointStream)
      .compile
      .toVector
      .map { zipped =>
        val finalEvents = zipped.lastOption.fold(FlightEvents.empty)(_._1)
        Result(zipped, finalEvents, JumpEventsSection(configHash, finalEvents.toStored))
      }

  private def loadFromSidecar[A](section: JumpEventsSection, points: Vector[A]): Result[A] =
    val events = section.events.toFlightEvents
    val rows   =
      points
        .zipWithIndex
        .map {
          case (point, index) =>
            (flightEventsAt(events, index.toLong), point)
        }

    Result(rows, events, section)

  private def flightEventsAt(events: FlightEvents, index: Long): FlightEvents =
    FlightEvents(
      takeoff = events.takeoff.filter(_.index <= index),
      freefall = events.freefall.filter(_.index <= index),
      canopy = events.canopy.filter(_.index <= index),
      landing = events.landing.filter(_.index <= index),
    )

}
