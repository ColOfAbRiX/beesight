package com.colofabrix.scala.beesight.swoop

import cats.effect.IO
import com.colofabrix.scala.beesight.config.{DetectionConfig, SwoopConfig}
import com.colofabrix.scala.beesight.jumpevents.{FlysightTestReader, JumpEventsDetector}
import com.colofabrix.scala.beesight.model.*
import java.io.File
import java.nio.file.Path

/**
 * Test infrastructure for validating swoop detection against gswoop output.
 *
 * Scans a directory for Flysight CSV/ZIP recordings, loads expected gswoop values from a summary CSV, and provides
 * helpers to detect flight events and swoop metrics for each recording.
 */
private[beesight] object SwoopTestReader {

  val testFilesDir: Path =
    java.nio.file.Paths.get("src/test/resources/test_files")

  val swoopDir: Path =
    java.nio.file.Paths.get("src/test/resources/test_files")

  val resultsCsvPath: Path =
    java.nio.file.Paths.get("src/test/resources/test_files/swoop_results.csv")

  def loadExpected(): List[GSwoopResult] =
    GSwoopResult.loadAll(resultsCsvPath)

  def collectFiles(): List[File] =
    val dir = swoopDir.toFile
    if (!dir.exists()) {
      Nil
    } else {
      dir.listFiles()
        .filter { f =>
          val name = f.getName.toLowerCase
          (name.endsWith(".csv") || name.endsWith(".zip")) &&
            name != "swoop_results.csv" && name != "jump_events_results.csv"
        }
        .sortBy(_.getName)
        .toList
    }

  def stream(file: File): fs2.Stream[IO, IndexedPoint] =
    FlysightTestReader.readCsvStream(file.toPath)

  def detectEvents(
    file: File,
    config: DetectionConfig = DetectionConfig.default,
  ): IO[FlightEvents] =
    stream(file)
      .through(JumpEventsDetector(config).streamDetect)
      .compile
      .last
      .map(_.getOrElse(FlightEvents.empty))

  def detectSwoop(
    file: File,
    config: DetectionConfig = DetectionConfig.default,
  ): IO[Option[SwoopData]] =
    for {
      events <- detectEvents(file, config)
      swoop  <- SwoopDataDetector(config.swoop).detect(events, stream(file))
    } yield swoop

  def expected(file: File, expectedValues: List[GSwoopResult]): Option[GSwoopResult] =
    expectedValues.find(_.filename == file.getName)

}
