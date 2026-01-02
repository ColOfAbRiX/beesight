package com.colofabrix.scala.beesight.config

import java.nio.file.Path

/**
 * Configuration for the command line tool
 *
 * @param input Input path (file or directory, supports glob patterns)
 * @param output Output directory (optional, defaults to sibling "processed" directory)
 * @param processLimit Limits the number of files to process
 * @param dryRun Do a test run without creating output files
 * @param bufferPoints Number of points to keep before or after a landing or takeoff has been detected
 * @param minRetainedPoints Percentage of minimum point the tool must keep
 */
final case class Config(
  input: Path,
  output: Option[Path],
  processLimit: Option[Int],
  dryRun: Boolean,
  bufferPoints: Int,
  minRetainedPoints: Double,
)
