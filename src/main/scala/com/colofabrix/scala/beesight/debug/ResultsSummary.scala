package com.colofabrix.scala.beesight.debug

import cats.effect.IO
import com.colofabrix.scala.beesight.*
import com.colofabrix.scala.beesight.files.FileOps
import com.colofabrix.scala.beesight.model.*
import java.nio.file.{ Files as JFiles, Path }

/**
 * Handles collection and writing of flight detection summary results
 */
object ResultsSummary {

  /**
   * Writes summary rows to a CSV file using OutputFlightPoint directly
   */
  def writeSummaryCsv[A](rows: List[(Path, Option[OutputFlightPoint[A]])]): IOConfig[Unit] =
    IOConfig.ask.flatMap { config =>
      if config.dryRun then
        IOConfig.pure(())
      else
        val header =
          List(
            "filename",
            "takeoff_pt",
            "takeoff_alt",
            "freefall_pt",
            "freefall_alt",
            "canopy_pt",
            "canopy_alt",
            "landing_pt",
            "landing_alt",
          )

        val csvRows = rows.map(rowToCsv)
        val content = (header.mkString(",") :: csvRows).mkString("\n")

        FileOps
          .createOutputDirectory(config.input.resolve("summary.csv"))
          .flatMap { summaryPath =>
            IOConfig.blocking(JFiles.writeString(summaryPath, content): Unit)
          }
    }

  private def rowToCsv[A](row: (Path, Option[OutputFlightPoint[A]])): String =
    val (inputFile, pointOpt) = row
    val filename              = inputFile.getFileName.toString

    val fields =
      pointOpt match {
        case Some(p) =>
          List(
            filename,
            p.takeoff.map(_.lineIndex.toString).getOrElse(""),
            p.takeoff.map(s => formatDouble(s.altitude)).getOrElse(""),
            p.freefall.map(_.lineIndex.toString).getOrElse(""),
            p.freefall.map(s => formatDouble(s.altitude)).getOrElse(""),
            p.canopy.map(_.lineIndex.toString).getOrElse(""),
            p.canopy.map(s => formatDouble(s.altitude)).getOrElse(""),
            p.landing.map(_.lineIndex.toString).getOrElse(""),
            p.landing.map(s => formatDouble(s.altitude)).getOrElse(""),
          )
        case None =>
          List(filename, "", "", "", "", "", "", "", "")
      }

    fields.mkString(",")

  private def formatDouble(d: Double): String =
    f"$d%.2f"

}
