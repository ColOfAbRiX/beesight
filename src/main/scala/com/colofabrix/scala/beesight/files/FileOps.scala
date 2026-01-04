package com.colofabrix.scala.beesight.files

import cats.effect.*
import cats.implicits.*
import com.colofabrix.scala.beesight.*
import java.nio.file.*
import scala.jdk.CollectionConverters.*

object FileOps {

  def discoverCsvFiles(input: Path): IO[List[Path]] =
    IO.blocking {
      val (baseDir, globPattern) = parseInputAsGlob(input)
      val matcher                = FileSystems.getDefault.getPathMatcher(s"glob:$globPattern")
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

  def computeOutputPath(inputFile: Path): IOConfig[Path] =
    IOConfig.ask.map { config =>
      val absoluteInput = inputFile.toAbsolutePath.normalize

      val inputBaseDir =
        if Files.isRegularFile(absoluteInput) then
          Option(absoluteInput.getParent).getOrElse(Paths.get("."))
        else
          absoluteInput

      lazy val relativePath = inputBaseDir.relativize(inputFile.toAbsolutePath.normalize)

      config
        .output
        .getOrElse {
          Option(inputBaseDir.getParent)
            .getOrElse(Paths.get("."))
            .resolve("processed")
            .resolve(inputBaseDir.getFileName)
        }
        .resolve(relativePath)
    }

  def computeChartPath(outputFile: Path): Path =
    val baseName =
      outputFile
        .getFileName
        .toString
        .replaceFirst("\\.[^.]+$", "")

    Option(outputFile.getParent)
      .getOrElse(Paths.get("."))
      .resolve(s"$baseName.html")

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
