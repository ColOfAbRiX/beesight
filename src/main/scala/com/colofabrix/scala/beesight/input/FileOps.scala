package com.colofabrix.scala.beesight.input

import cats.effect.*
import com.colofabrix.scala.beesight.*
import java.nio.file.*
import scala.jdk.CollectionConverters.*
import com.colofabrix.scala.beesight.output.WorkingPaths

/**
 * File system operations for discovering input files and creating output directories.
 */
private[beesight] object FileOps {

  /**
   * Discovers all CSV and ZIP files matching the configured input path, applying the process limit if set.
   */
  def discoverCsvFiles(): IOConfig[List[Path]] =
    IOConfig.ask.flatMap { config =>
      val (baseDir, glob) = WorkingPaths.parseInputAsGlob(config.global.input)
      val matcher         = FileSystems.getDefault.getPathMatcher(s"glob:${glob.toLowerCase}")

      IOConfig
        .blocking {
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
        .map { files =>
          config
            .global
            .processLimit
            .fold(files)(limit => files.take(limit.value))
        }
    }

  /**
   * Creates the parent directory of the processed CSV
   */
  def createOutputDirectory(paths: WorkingPaths): IOConfig[Unit] =
    IOConfig
      .blocking {
        Files.createDirectories(paths.outputFile.getParent)
      }
      .map(_ => ())
}
