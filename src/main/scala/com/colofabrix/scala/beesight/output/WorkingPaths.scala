package com.colofabrix.scala.beesight.output

import java.nio.file.Path
import com.colofabrix.scala.beesight.IOConfig
import com.colofabrix.scala.beesight.input.FileOps

/**
 * Paths involved in the processing of a single input file.
 *
 * @param inputFile The file being processed
 * @param outputFile The processed CSV produced next to the input data
 */
final class WorkingPaths private[output] (val inputFile: Path, val outputFile: Path)

object WorkingPaths {

  def apply(inputFile: Path): IOConfig[WorkingPaths] =
    FileOps
      .computeOutputPath(inputFile)
      .map(new WorkingPaths(inputFile, _))

  extension (self: WorkingPaths) {

    /** Interactive HTML chart of the flight */
    def chartHtml: Path =
      sibling(self.outputFile, ".html")

    /** The same data converted to the FlySight CSV format */
    def flysightCsv: Path =
      sibling(self.outputFile, ".flysight.csv")

    /** Per-file flight metrics export */
    def metricsCsv: Path =
      sibling(self.outputFile, ".metrics.csv")

    /** Swoop landing analysis report as JSON */
    def swoopJson: Path =
      sibling(self.outputFile, ".swoop.json")

    /** Metadata sidecar caching the analysis of the input file, keeping the full input name */
    def sidecar: Path =
      self.inputFile.resolveSibling(s"${self.inputFile.getFileName}.meta")

    private def sibling(file: Path, suffix: String): Path = {
      val baseName = file.getFileName.toString.replaceFirst("\\.[^.]+$", "")
      file.resolveSibling(s"$baseName$suffix")
    }

  }

}
