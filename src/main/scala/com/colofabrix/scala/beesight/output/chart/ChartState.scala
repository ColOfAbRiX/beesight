package com.colofabrix.scala.beesight.output.chart

import com.colofabrix.scala.beesight.detection.model.FlightEvents
import java.time.Instant

/**
 * The data collected while streaming the points, enough to render the chart.
 */
final private[beesight] case class ChartState[A](
  times: Vector[Instant],
  altitudes: Vector[Double],
  velDs: Vector[Double],
  stages: FlightEvents,
)

private[beesight] object ChartState {

  def empty[A]: ChartState[A] =
    ChartState(Vector.empty, Vector.empty, Vector.empty, FlightEvents.empty)

}
