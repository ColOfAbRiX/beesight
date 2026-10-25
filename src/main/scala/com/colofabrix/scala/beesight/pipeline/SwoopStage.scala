package com.colofabrix.scala.beesight.pipeline

import cats.effect.IO
import cats.syntax.all.*
import com.colofabrix.scala.beesight.*
import com.colofabrix.scala.beesight.IOConfig
import com.colofabrix.scala.beesight.config.Config
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.detection.readers.*
import com.colofabrix.scala.beesight.detection.swoop.SwoopAnalyser
import com.colofabrix.scala.beesight.metadata.*
import com.colofabrix.scala.beesight.metadata.ConfigHash.*
import com.colofabrix.scala.beesight.metadata.StoredConversions.*

/**
 * Runs the swoop analysis stage over the whole input, reusing the cached result when the sidecar section is fresh.
 * The analysis cuts its own canopy-to-landing window, so its freshness depends on the detection configuration too.
 */
object SwoopStage {

  final case class Result(
    swoop: Option[SwoopData],
    section: Option[SwoopSection],
  )

  def run[A: PointReader](
    points: Vector[A],
    events: FlightEvents,
    sidecar: Sidecar,
  ): IOConfig[Result] =
    IOConfig
      .ask
      .flatMap { config =>
        val stages = config.command.resolvedStages

        if stages.wants(Stage.Swoop) || stages.wants(Stage.Charts) then
          val configHash = config.detectionConfig.swoop.configHash(config.detectionConfig.jumpevents)

          fromSidecar(sidecar, configHash).getOrElse(compute[A](config, points, events, configHash))
        else
          Result(None, None).pure[IOConfig]
      }

  private def fromSidecar(sidecar: Sidecar, configHash: ConfigHash): Option[IOConfig[Result]] =
    for
      section <- sidecar.sections.swoop.filter(_.configHash === configHash)
      swoop   <- section.result.toSwoopData.toOption
    yield Result(Some(swoop), Some(section)).pure[IOConfig]

  private def compute[A: PointReader](
    cfg: Config,
    points: Vector[A],
    events: FlightEvents,
    configHash: ConfigHash,
  ): IOConfig[Result] =
    SwoopAnalyser(cfg.detectionConfig.swoop)
      .analyse[IO, A](events, fs2.Stream.emits[IO, A](points))
      .to[IOConfig]
      .map { swoop =>
        Result(swoop, swoop.map(data => SwoopSection(configHash, data.toStored)))
      }

}
