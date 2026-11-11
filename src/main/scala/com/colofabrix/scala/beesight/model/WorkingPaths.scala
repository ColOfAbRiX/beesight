package com.colofabrix.scala.beesight.model

import com.colofabrix.scala.beesight.IOConfig
import com.colofabrix.scala.beesight.config.Config
import java.nio.file.{ FileSystems, Files, Path, Paths }
import scala.jdk.CollectionConverters.*


/**
 * Paths involved in the processing of a single input file.
 *
 * @param inputFile The file being processed
 * @param outputFile The processed CSV produced next to the input data
 */
final class WorkingPaths private[beesight] (val inputFile: Path, val outputFile: Path)

object WorkingPaths {

  def apply(inputFile: Path): IOConfig[WorkingPaths] =
    computeOutputPath(inputFile).map(new WorkingPaths(inputFile, _))

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

    /** Metadata file caching the analysis of the input file, keeping the full input name */
    def metadataFile: Path =
      self.inputFile.resolveSibling(s"${self.inputFile.getFileName}.meta")

    private def sibling(file: Path, suffix: String): Path = {
      val baseName = file.getFileName.toString.replaceFirst("\\.[^.]+$", "")
      file.resolveSibling(s"$baseName$suffix")
    }

  }

  private def computeOutputPath(inputFile: Path): IOConfig[Path] =
    IOConfig.ask.map { config =>
      val (inputBaseDir, _) = parseInputAsGlob(config.global.input)
      val parent            = Option(inputBaseDir.getParent).getOrElse(Paths.get("."))
      val inputDirName      = inputBaseDir.getFileName
      val relativePath      = inputBaseDir.relativize(inputFile.toAbsolutePath.normalize)
      val relativeDir       = Option(relativePath.getParent).getOrElse(Paths.get(""))

      val outputRoot     = config.global.output.getOrElse(parent)
      val inputParent    = inputFile.toAbsolutePath.normalize.getParent
      val sameDirectory  = outputRoot.toAbsolutePath.normalize == inputParent

      val resolved =
        if sameDirectory then outputRoot.resolve(processedFileName(inputFile)).toAbsolutePath.normalize
        else
          outputRoot
            .resolve(inputDirName)
            .resolve(relativeDir)
            .resolve(processedFileName(inputFile))
            .toAbsolutePath
            .normalize

      if resolved == inputFile.toAbsolutePath.normalize then suffixed(resolved, "_output") else resolved
    }

  private def suffixed(file: Path, suffix: String): Path = {
    val name = file.getFileName.toString
    val dot  = name.lastIndexOf('.')
    val base = if dot > 0 then name.substring(0, dot) else name
    val ext  = if dot > 0 then name.substring(dot) else ""
    file.resolveSibling(s"$base$suffix$ext")
  }

  private def processedFileName(inputFile: Path): Path =
    val name = inputFile.getFileName.toString

    if name.toLowerCase.endsWith(".zip") then
      Paths.get(name.substring(0, name.length - 4) + ".csv")
    else
      inputFile.getFileName

  /** True when the outputs are written into the input directory itself */
  private[beesight] def writesInPlace(config: Config): Boolean = {
    val (baseDir, _) = parseInputAsGlob(config.global.input)
    val outputRoot   = config.global.output.getOrElse(Option(baseDir.getParent).getOrElse(Paths.get(".")))
    val normalized   = outputRoot.toAbsolutePath.normalize

    normalized == baseDir.toAbsolutePath.normalize ||
    normalized.resolve(baseDir.getFileName).toAbsolutePath.normalize == baseDir.toAbsolutePath.normalize
  }

  /** True when the CSV matches an artefact the tool writes next to its inputs */
  private[beesight] def isKnownOutput(file: Path): Boolean = {
    val lower = file.getFileName.toString.toLowerCase

    if !lower.endsWith(".csv") then false
    else {
      val stem = file.getFileName.toString.replaceFirst("\\.[^.]+$", "")

      lower.endsWith(".metrics.csv") ||
      lower.endsWith(".flysight.csv") ||
      stem.toLowerCase.endsWith("_output") ||
      Files.isRegularFile(file.resolveSibling(s"$stem.zip"))
    }
  }

  private[beesight] def parseInputAsGlob(input: Path): (Path, String) =
    val absoluteInput = input.toAbsolutePath.normalize
    val inputStr      = absoluteInput.toString

    if Files.isRegularFile(absoluteInput) then
      val parent = Option(absoluteInput.getParent).getOrElse(Paths.get("."))
      (parent, absoluteInput.getFileName.toString)
    else if Files.isDirectory(absoluteInput) then
      (absoluteInput, "{*.csv,*.zip,**/*.csv,**/*.zip}")
    else if inputStr.contains("*") || inputStr.contains("?") then
      val idx     = inputStr.indexWhere(c => c == '*' || c == '?')
      val lastSep = inputStr.lastIndexOf(FileSystems.getDefault.getSeparator, idx)

      if lastSep >= 0 then
        (Paths.get(inputStr.substring(0, lastSep)), inputStr.substring(lastSep + 1))
      else
        (Paths.get(".").toAbsolutePath.normalize, inputStr)
    else
      val parent = Option(absoluteInput.getParent).getOrElse(Paths.get("."))
      (parent, absoluteInput.getFileName.toString)

}
