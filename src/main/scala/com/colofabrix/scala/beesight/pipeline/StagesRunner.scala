package com.colofabrix.scala.beesight.pipeline

import cats.effect.IO
import cats.syntax.all.*
import com.colofabrix.scala.beesight.*
import com.colofabrix.scala.beesight.IOConfig
import com.colofabrix.scala.beesight.config.Config
import com.colofabrix.scala.beesight.detection.jumpevents.JumpEventsDetector
import com.colofabrix.scala.beesight.detection.model.FlightEvents
import com.colofabrix.scala.beesight.detection.readers.PointReader
import com.colofabrix.scala.beesight.input.CsvFileReader
import com.colofabrix.scala.beesight.metadata.*
import com.colofabrix.scala.beesight.metadata.StoredConversions.*
import com.colofabrix.scala.beesight.output.*
import fs2.data.csv.{ CsvRowDecoder, CsvRowEncoder }
import java.nio.file.Path

/**
 * Executes the canonical stages of the processing pipeline for one input file, caching the analysis in the sidecar
 * of the file.
 */
object StagesRunner {

  final private case class Detected[A](
    rows: Vector[(FlightEvents, A)],
    sidecar: Sidecar,
  )

  def run[A: PointReader](
    cfg: Config,
    inputFile: Path,
    paths: OutputPaths,
  )(using
    CsvRowDecoder[A, String],
    CsvRowEncoder[A, String],
  ): IOConfig[Unit] =
    for
      fingerprint     <- InputFingerprint.of(inputFile).to[IOConfig]
      existingSidecar <- SidecarStore.load(paths.sidecar, fingerprint).to[IOConfig]
      points          <- CsvFileReader.readCsv[A](inputFile).compile.toVector
      detected        <- detect[A](cfg, points, existingSidecar)
      cutResult        = DataCutter(cfg).cutWithSummary(detected.rows)
      cutStored        = cutResult.summary.toStored
      cutHash          = ConfigHash.sha256(cfg.dataCutterConfig.toString)
      newSidecar       = detected.sidecar.withCut(CutSection(cutHash, cutStored))
      _               <- broadcastOutputs[A](cfg, cutResult.retained, paths)
      _               <- IOConfig.blocking(SidecarStore.save(newSidecar, paths.sidecar)).whenA(!cfg.global.dryRun)
    yield ()

  /**
   * Builds the per-point rows, reusing the cached detection when the sidecar section is fresh.
   */
  private def detect[A: PointReader](cfg: Config, points: Vector[A], sidecar: Sidecar): IOConfig[Detected[A]] =
    val configHash = ConfigHash.sha256(cfg.detectionConfig.jumpevents.toString)
    sidecar
      .sections
      .jumpevents
      .filter(_.configHash === configHash)
      .fold(runEventsDetection(cfg, points, sidecar)) { jumpEventsSection =>
        loadSidecar(jumpEventsSection, points, sidecar)
      }

  private def loadSidecar[A](section: JumpEventsSection, points: Vector[A], sidecar: Sidecar): IOConfig[Detected[A]] =
    val rows =
      points
        .zipWithIndex
        .map {
          case (point, index) =>
            val events = section.events.toFlightEvents
            (flightEventsAt(events, index.toLong), point)
        }

    Detected[A](rows, sidecar).pure[IOConfig]

  private def runEventsDetection[A: PointReader](
    cfg: Config,
    points: Vector[A],
    sidecar: Sidecar
  ): IOConfig[Detected[A]] =
    val detector    = JumpEventsDetector(cfg.detectionConfig.jumpevents)
    val pointStream = fs2.Stream.emits[IOConfig, A](points)
    val eventsHash  = ConfigHash.sha256(cfg.detectionConfig.jumpevents.toString)

    (pointStream.through(detector.detect) zip pointStream)
      .compile
      .toVector
      .map { zipped =>
        val finalEvents = zipped.lastOption.fold(FlightEvents.empty)(_._1)
        val stored      = finalEvents.toStored
        Detected(zipped, sidecar.withJumpevents(JumpEventsSection(eventsHash, stored)))
      }

  private def broadcastOutputs[A: PointReader: CsvRowEncoder[*, String]](
    cfg: Config,
    retained: Vector[(FlightEvents, A)],
    paths: OutputPaths,
  ): IOConfig[Unit] =
    fs2.Stream
      .emits(retained)
      .broadcastThrough(
        CsvResultWriter(cfg).writePipe(paths.processedCsv),
        ChartGenerator(cfg).chartPipe(paths.chartHtml),
        ResultPrinter(cfg).printStagesPipe,
      )
      .compile
      .drain

  private def flightEventsAt(events: FlightEvents, index: Long): FlightEvents =
    FlightEvents(
      takeoff = events.takeoff.filter(_.index <= index),
      freefall = events.freefall.filter(_.index <= index),
      canopy = events.canopy.filter(_.index <= index),
      landing = events.landing.filter(_.index <= index),
    )

}
