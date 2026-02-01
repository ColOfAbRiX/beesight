package com.colofabrix.scala.beesight.detection

import com.colofabrix.scala.beesight.collections.FixedSizeQueue
import com.colofabrix.scala.beesight.detection.model.VerticalSpeedSample
import com.colofabrix.scala.beesight.model.FlightPoint

object InflectionFinder {

  // ─── Debug Configuration ───────────────────────────────────────────────────

  private val DEBUG_ENABLED = false

  private def debug(msg: => String): Unit =
    if (DEBUG_ENABLED) println(s"[INFLECTION] $msg")

  // ─── Public API ────────────────────────────────────────────────────────────

  def findInflectionPoint(
    window: FixedSizeQueue[VerticalSpeedSample],
    isRising: Boolean,
    minSpeedDelta: Double = 1.0,
  ): Option[FlightPoint] = {
    val samples = window.toVector

    if (samples.size < 2) {
      debug(s"Window too small (${samples.size} samples), returning head")
      samples.headOption.map(s => FlightPoint(s.index, s.altitude))
    } else {
      val pairs = samples.sliding(2).toVector

      debug(s"Searching for inflection (isRising=$isRising, minDelta=$minSpeedDelta)")
      debug(s"Window contents (${samples.size} samples):")
      samples.zipWithIndex.foreach { case (s, i) =>
        debug(f"  [$i] index=${s.index}%d, speed=${s.speed}%.2f m/s, alt=${s.altitude}%.1f m")
      }

      // Find inflection point with minimum speed delta threshold
      val inflectionIndex =
        pairs.zipWithIndex.collectFirst {
          case (Vector(prev, curr), idx) if meetsInflectionCriteria(prev.speed, curr.speed, isRising, minSpeedDelta) =>
            debug(f"  → Inflection found at pair[$idx]: prev=${prev.speed}%.2f, curr=${curr.speed}%.2f, delta=${(curr.speed - prev.speed).abs}%.2f")
            idx
        }

      inflectionIndex match {
        case Some(idx) =>
          val sample = samples(idx)
          debug(f"Returning sample[$idx]: index=${sample.index}, altitude=${sample.altitude}%.1f")
          Some(FlightPoint(sample.index, sample.altitude))
        case None =>
          debug("No inflection found matching criteria, returning head")
          samples.headOption.map(s => FlightPoint(s.index, s.altitude))
      }
    }
  }

  private def meetsInflectionCriteria(
    prevSpeed: Double,
    currSpeed: Double,
    isRising: Boolean,
    minDelta: Double,
  ): Boolean = {
    val delta = currSpeed - prevSpeed
    if (isRising) delta > minDelta
    else delta < -minDelta
  }

}
