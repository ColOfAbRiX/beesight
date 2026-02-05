package com.colofabrix.scala.beesight.detection

import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.model.*
import cats.data.Reader

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

  def streamDetectA[F[_], A](using A: FileFormatAdapter[A]): fs2.Pipe[F, A, OutputFlightRow[A]] =
    streamDetectWithConfig(DetectionConfig.default)

  def streamDetectWithConfig[F[_], A: FileFormatAdapter](config: DetectionConfig): fs2.Pipe[F, A, OutputFlightRow[A]] =
    stream =>
      println(config)
      println(implicitly[FileFormatAdapter[A]])
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
