package com.colofabrix.scala.beesight

import com.colofabrix.scala.beesight.detection.FlightStagesDetection
import com.colofabrix.scala.beesight.model.FlightEvents
import com.colofabrix.scala.beesight.model.formats.FlysightPoint
import java.io.File
import scala.io.Source

object Tools {

  def simpleCsvRead[A](file: File)(f: (Map[String, Int], Array[String]) => A): List[A] =
    val lines =
      Source
        .fromFile(file)
        .getLines()
        .toList
        .filterNot(_.isBlank)

    val header =
      lines
        .head
        .split(",")
        .zipWithIndex
        .toMap

    lines
      .tail
      .filter(_.trim.nonEmpty)
      .map { line =>
        val cols = line.split(",", -1)
        f(header, cols)
      }

  def detectPoints(data: fs2.Stream[IOConfig, FlysightPoint]): IOConfig[FlightEvents] =
    data
      .through(FlightStagesDetection.streamDetectA)
      .compile
      .last
      .map {
        _.fold(FlightEvents.empty) { output =>
          FlightEvents(
            takeoff = output.takeoff,
            freefall = output.freefall,
            canopy = output.canopy,
            landing = output.landing,
          )
        }
      }

}
