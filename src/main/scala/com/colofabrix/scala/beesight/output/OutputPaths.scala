package com.colofabrix.scala.beesight.output

import java.nio.file.Path

/**
 * Output file names derived from a single input file.
 *
 * @param inputFile The file being processed
 * @param processedCsv The processed CSV produced next to the input data
 */
final case class OutputPaths(inputFile: Path, processedCsv: Path) {

  /** Interactive HTML chart of the flight */
  def chartHtml: Path =
    sibling(processedCsv, ".html")

  /** The same data converted to the FlySight CSV format */
  def flysightCsv: Path =
    sibling(processedCsv, ".flysight.csv")

  /** Per-file flight metrics export */
  def metricsCsv: Path =
    sibling(processedCsv, ".metrics.csv")

  /** Swoop landing analysis report as JSON */
  def swoopJson: Path =
    sibling(processedCsv, ".swoop.json")

  /** Metadata sidecar caching the analysis of the input file, keeping the full input name */
  def sidecar: Path =
    inputFile.resolveSibling(s"${inputFile.getFileName}.meta")

  private def sibling(file: Path, suffix: String): Path = {
    val baseName = file.getFileName.toString.replaceFirst("\\.[^.]+$", "")
    file.resolveSibling(s"$baseName$suffix")
  }

}
