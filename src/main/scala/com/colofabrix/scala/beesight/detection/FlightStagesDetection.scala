package com.colofabrix.scala.beesight.detection

import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.model.*
import cats.data.Reader

/**
 * Detects flight stages (takeoff, freefall, canopy, landing) from flight data streams.
 */
object FlightStagesDetection {

  // ─── Debug Configuration ───────────────────────────────────────────────────

  private val DEBUG_ENABLED = false

  private def debug(msg: => String): Unit =
    if (DEBUG_ENABLED) println(s"[DEBUG] $msg")

  private def debugSection(title: String)(body: => Unit): Unit =
    if (DEBUG_ENABLED) {
      println(s"\n${"=" * 60}")
      println(s"  $title")
      println(s"${"=" * 60}")
      body
    }

  // ─── Public API ────────────────────────────────────────────────────────────

  /**
   * Streaming pipe that detects flight stages using default configuration.
   */
  def streamDetectA[F[_], A](using A: FileFormatAdapter[A]): fs2.Pipe[F, A, OutputFlightRow[A]] =
    streamDetectWithConfig(DetectionConfig.default)

  /**
   * Streaming pipe that detects flight stages using the provided configuration.
   */
  @scala.annotation.nowarn
  def streamDetectWithConfig[F[_], A: FileFormatAdapter](config: DetectionConfig): fs2.Pipe[F, A, OutputFlightRow[A]] =
    stream =>
      // println(config)
      // println(implicitly[FileFormatAdapter[A]])
      stream.map { a =>
        OutputFlightRow(
          takeoff = None,
          freefall = None,
          canopy = None,
          landing = None,
          source = a,
        )
      }

}
