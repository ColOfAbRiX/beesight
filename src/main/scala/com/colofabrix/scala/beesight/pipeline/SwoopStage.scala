package com.colofabrix.scala.beesight.pipeline

import cats.effect.IO
import cats.syntax.all.*
import com.colofabrix.scala.beesight.*
import com.colofabrix.scala.beesight.IOConfig
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.detection.readers.*
import com.colofabrix.scala.beesight.detection.swoop.SwoopAnalyser

/**
 * Runs the swoop analysis stage over the whole input. The analysis is a pure function of the input and the
 * configuration, so it is computed fresh every time: the metadata file caches only the detection results.
 */
object SwoopStage {

  final case class AnalysedSwoop(
    swoop: Option[SwoopData],
  )

  def run[A: PointReader](points: Vector[A], events: FlightEvents): IOConfig[AnalysedSwoop] =
    IOConfig
      .ask
      .flatMap { config =>
        val stages = config.command.resolvedStages

        if stages.wants(Stage.Swoop) || stages.wants(Stage.Charts) then
          SwoopAnalyser(config.detectionConfig.swoop)
            .analyse[IO, A](events, fs2.Stream.emits[IO, A](points))
            .to[IOConfig]
            .map(swoop => AnalysedSwoop(swoop))
        else
          AnalysedSwoop(None).pure[IOConfig]
      }

}
