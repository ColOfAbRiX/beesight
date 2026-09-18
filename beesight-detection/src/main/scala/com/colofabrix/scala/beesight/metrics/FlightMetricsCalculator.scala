package com.colofabrix.scala.beesight.metrics

import com.colofabrix.scala.beesight.model.*

final class FlightMetricsCalculator private () {

  def compute[F[_], A: PointReader]: fs2.Pipe[F, A, FlightMetric] =
    ???

}

object FlightMetricsCalculator {

  def apply(): FlightMetricsCalculator =
    new FlightMetricsCalculator()

}
