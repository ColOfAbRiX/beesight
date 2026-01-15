package com.colofabrix.scala.beesight.files

import cats.effect.*
import cats.implicits.*
import com.colofabrix.scala.beesight.*
import java.nio.file.*
import scala.jdk.CollectionConverters.*

object FileOps {

  def discoverCsvFiles(): IOConfig[List[Path]] =
    IOConfig.ask.flatMap { config =>
      val (baseDir, glob) = parseInputAsGlob(config.input)
      val matcher         = FileSystems.getDefault.getPathMatcher(s"glob:$glob")

      IOConfig.blocking {
        Files
          .walk(baseDir)
          .iterator()
          .asScala
          .filter(Files.isRegularFile(_))
          .filter { p =>
            val rel = baseDir.relativize(p)
            matcher.matches(Paths.get(rel.toString.toLowerCase))
          }
          .toList
          .sorted
      }
    }

  /**
   * Computes the output path and creates parent directories
   */
  def createProcessedDirectory(inputFile: Path): IOConfig[Path] =
    for
      outputPath <- computeProcessedPath(inputFile)
      _          <- IOConfig.blocking(Files.createDirectories(outputPath.getParent))
    yield outputPath

  /**
   * Computes the output path for a given input file
   */
  def computeProcessedPath(inputFile: Path): IOConfig[Path] =
    IOConfig.ask.map { config =>
      val (inputBaseDir, _) = parseInputAsGlob(config.input)
      val parent            = Option(inputBaseDir.getParent).getOrElse(Paths.get("."))
      val inputDirName      = inputBaseDir.getFileName
      val relativePath      = inputBaseDir.relativize(inputFile.toAbsolutePath.normalize)

      config
        .output
        .getOrElse(parent.resolve("processed"))
        .resolve(inputDirName)
        .resolve(relativePath)
        .toAbsolutePath
        .normalize
    }

  private def parseInputAsGlob(input: Path): (Path, String) =
    val absoluteInput = input.toAbsolutePath.normalize
    val inputStr      = absoluteInput.toString

    if Files.isRegularFile(absoluteInput) then
      val parent = Option(absoluteInput.getParent).getOrElse(Paths.get("."))
      (parent, absoluteInput.getFileName.toString)
    else if Files.isDirectory(absoluteInput) then
      (absoluteInput, "{*.csv,**/*.csv}")
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
